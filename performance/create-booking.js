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

const bookingCreateDuration = new Trend("booking_create_duration", true);

export const options = {
  scenarios: {
    create_booking_flow: {
      executor: "ramping-vus",
      stages: stagesFromEnv(),
      gracefulRampDown: "30s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.03"],
    checks: ["rate>0.97"],
    "http_req_duration{endpoint:create_booking}": ["avg<700", "med<600", "p(95)<1200"],
    booking_create_duration: ["avg<700", "p(95)<1200"],
  },
};

export function setup() {
  const manager = login("manager@booking.local", "Password123!", "auth_login_setup");
  assertSetup(manager.response, "manager login");
  const managerToken = manager.token;

  const resourceName = `k6-room-${Date.now()}`;
  const resource = createResource(managerToken, resourceName);
  assertSetup(resource.response, "resource create");

  const user = registerRandomUser("k6-booking");
  assertSetup(user.response, "register user");

  return {
    resourceId: resource.id,
    userToken: user.token,
  };
}

export default function (data) {
  if (!data?.resourceId || !data?.userToken) {
    fail("setup data is missing");
  }

  const slot = bookingSlotIso(__VU, __ITER, 30);
  const res = http.post(
    `${BASE_URL}/api/bookings`,
    JSON.stringify({
      resourceId: data.resourceId,
      startTime: slot.startTime,
      endTime: slot.endTime,
    }),
    jsonParams(data.userToken, "create_booking"),
  );

  bookingCreateDuration.add(res.timings.duration);
  check(res, {
    "booking status 201": (r) => r.status === 201,
    "booking status is PENDING": (r) => r.status === 201 && r.json("status") === "PENDING",
    "booking has id": (r) => r.status === 201 && !!r.json("id"),
  });

  sleep(0.5);
}

