import http from "k6/http";
import { check, fail } from "k6";

export const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

export function jsonParams(token, endpointTag) {
  const headers = {
    "Content-Type": "application/json",
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return {
    headers,
    tags: endpointTag ? { endpoint: endpointTag } : {},
  };
}

export function assertSetup(res, name) {
  const ok = check(res, {
    [`${name} setup status is 2xx`]: (r) => r.status >= 200 && r.status < 300,
  });
  if (!ok) {
    fail(`${name} setup failed: ${res.status} ${res.body}`);
  }
}

export function login(email, password, endpointTag = "auth_login") {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email, password }),
    jsonParams(null, endpointTag),
  );
  return {
    response: res,
    token: res.status === 200 ? res.json("accessToken") : null,
  };
}

export function registerRandomUser(prefix = "k6-user") {
  const entropy = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const email = `${prefix}-${entropy}@test.local`;
  const password = "Password123!";
  const res = http.post(
    `${BASE_URL}/api/auth/register`,
    JSON.stringify({ email, password }),
    jsonParams(null, "auth_register"),
  );
  return {
    email,
    password,
    response: res,
    token: res.status === 201 ? res.json("accessToken") : null,
  };
}

export function createResource(managerToken, name) {
  const res = http.post(
    `${BASE_URL}/api/resources`,
    JSON.stringify({
      name,
      description: "Load test resource",
      capacity: 8,
      restricted: false,
    }),
    jsonParams(managerToken, "resource_create"),
  );
  return {
    response: res,
    id: res.status === 201 ? res.json("id") : null,
  };
}

export function bookingSlotIso(vu, iter, durationMinutes = 30) {
  const slotIndex = vu * 100000 + iter;
  const offsetMinutes = 24 * 60 + slotIndex * (durationMinutes + 5);
  const start = new Date(Date.now() + offsetMinutes * 60 * 1000);
  const end = new Date(start.getTime() + durationMinutes * 60 * 1000);
  return {
    startTime: start.toISOString(),
    endTime: end.toISOString(),
  };
}

