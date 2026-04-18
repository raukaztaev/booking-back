import http from "k6/http";
import { check, fail, sleep } from "k6";
import { Trend } from "k6/metrics";
import {
  assertSetup,
  BASE_URL,
  bookingSlotIso,
  createResource,
  jsonParams,
  login,
  registerRandomUser,
} from "./lib/api.js";
import { stagesFromEnv } from "./lib/load-profile.js";

const bookingConfirmDuration = new Trend("booking_confirm_duration", true);
const protectedEndpointDuration = new Trend("protected_endpoint_duration", true);

export const options = {
  scenarios: {
    protected_and_rbac_flow: {
      executor: "ramping-vus",
      stages: stagesFromEnv(),
      gracefulRampDown: "30s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.03"],
    checks: ["rate>0.96"],
    "http_req_duration{endpoint:booking_confirm}": ["avg<800", "med<700", "p(95)<1300"],
    "http_req_duration{endpoint:users_list_admin}": ["avg<700", "p(95)<1200"],
    "http_req_duration{endpoint:users_list_forbidden}": ["avg<700", "p(95)<1200"],
    booking_confirm_duration: ["avg<800", "p(95)<1300"],
    protected_endpoint_duration: ["avg<700", "p(95)<1200"],
  },
};

export function setup() {
  const manager = login("manager@booking.local", "Password123!", "auth_login_setup");
  const admin = login("admin@booking.local", "Password123!", "auth_login_setup");
  assertSetup(manager.response, "manager login");
  assertSetup(admin.response, "admin login");

  const resource = createResource(manager.token, `k6-protected-${Date.now()}`);
  assertSetup(resource.response, "resource create");

  const user = registerRandomUser("k6-protected");
  assertSetup(user.response, "register user");

  return {
    managerToken: manager.token,
    adminToken: admin.token,
    userToken: user.token,
    resourceId: resource.id,
  };
}

export default function (data) {
  if (!data?.managerToken || !data?.adminToken || !data?.userToken || !data?.resourceId) {
    fail("setup data is missing");
  }

  const slot = bookingSlotIso(__VU, __ITER, 25);
  const bookingRes = http.post(
    `${BASE_URL}/api/bookings`,
    JSON.stringify({
      resourceId: data.resourceId,
      startTime: slot.startTime,
      endTime: slot.endTime,
    }),
    jsonParams(data.userToken, "create_booking"),
  );

  const bookingCreated = check(bookingRes, {
    "booking for confirmation created": (r) => r.status === 201 && !!r.json("id"),
  });

  let confirmOk = false;
  if (bookingCreated) {
    const bookingId = bookingRes.json("id");
    const confirmRes = http.patch(
      `${BASE_URL}/api/bookings/${bookingId}/status`,
      JSON.stringify({ status: "CONFIRMED" }),
      jsonParams(data.managerToken, "booking_confirm"),
    );
    bookingConfirmDuration.add(confirmRes.timings.duration);
    confirmOk = check(confirmRes, {
      "manager confirms booking": (r) => r.status === 200 && r.json("status") === "CONFIRMED",
    });
  }

  const managerForbiddenRes = http.get(
    `${BASE_URL}/api/users?page=0&size=5`,
    jsonParams(data.managerToken, "users_list_forbidden"),
  );
  protectedEndpointDuration.add(managerForbiddenRes.timings.duration);
  const forbiddenOk = check(managerForbiddenRes, {
    "manager cannot list users": (r) => r.status === 403,
  });

  const adminAllowedRes = http.get(
    `${BASE_URL}/api/users?page=0&size=5`,
    jsonParams(data.adminToken, "users_list_admin"),
  );
  protectedEndpointDuration.add(adminAllowedRes.timings.duration);
  const adminOk = check(adminAllowedRes, {
    "admin can list users": (r) => r.status === 200,
  });

  check(
    { bookingCreated, confirmOk, forbiddenOk, adminOk },
    {
      "protected flow overall passed": (r) => r.bookingCreated && r.confirmOk && r.forbiddenOk && r.adminOk,
    },
  );

  sleep(0.5);
}

