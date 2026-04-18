import { check, sleep } from "k6";
import { Trend } from "k6/metrics";
import { login } from "./lib/api.js";
import { stagesFromEnv } from "./lib/load-profile.js";

const loginDuration = new Trend("auth_login_duration", true);

export const options = {
  scenarios: {
    auth_login_flow: {
      executor: "ramping-vus",
      stages: stagesFromEnv(),
      gracefulRampDown: "30s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.02"],
    checks: ["rate>0.98"],
    "http_req_duration{endpoint:auth_login}": ["avg<500", "med<400", "p(95)<900"],
    auth_login_duration: ["avg<500", "p(95)<900"],
  },
};

const LOGIN_EMAIL = __ENV.LOGIN_EMAIL || "manager@booking.local";
const LOGIN_PASSWORD = __ENV.LOGIN_PASSWORD || "Password123!";

export default function () {
  const { response } = login(LOGIN_EMAIL, LOGIN_PASSWORD, "auth_login");
  loginDuration.add(response.timings.duration);
  check(response, {
    "login status 200": (r) => r.status === 200,
    "access token exists": (r) => !!r.json("accessToken"),
    "refresh token exists": (r) => !!r.json("refreshToken"),
  });
  sleep(1);
}

