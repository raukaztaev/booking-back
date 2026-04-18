const LOAD_PROFILES = {
  normal: [
    { duration: "30s", target: 10 },
    { duration: "2m", target: 10 },
    { duration: "30s", target: 0 },
  ],
  peak: [
    { duration: "45s", target: 30 },
    { duration: "3m", target: 30 },
    { duration: "45s", target: 0 },
  ],
  spike: [
    { duration: "20s", target: 10 },
    { duration: "20s", target: 80 },
    { duration: "1m", target: 80 },
    { duration: "20s", target: 10 },
    { duration: "20s", target: 0 },
  ],
  endurance: [
    { duration: "1m", target: 10 },
    { duration: "10m", target: 10 },
    { duration: "30s", target: 0 },
  ],
};

export function stagesFromEnv() {
  const profile = (__ENV.LOAD_PROFILE || "normal").toLowerCase();
  return LOAD_PROFILES[profile] || LOAD_PROFILES.normal;
}

