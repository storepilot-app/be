import http from "k6/http";
import { check, fail, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const virtualUsers = Number(__ENV.VUS || 1);
const pollSeconds = Number(__ENV.POLL_SECONDS || 2);
const jobTimeoutSeconds = Number(__ENV.JOB_TIMEOUT_SECONDS || 1200);
const excelFilePath = __ENV.EXCEL_FILE || "./fixtures/products-small.xlsx";

const users = JSON.parse(open("./users.local.json"));
const excelFile = open(excelFilePath, "b");

const jobDuration = new Trend("job_duration_ms", true);
const jobQueueWait = new Trend("job_queue_wait_ms", true);
const jobFailed = new Rate("job_failed");

if (!Number.isInteger(virtualUsers) || virtualUsers < 1) {
  throw new Error("VUS must be a positive integer.");
}

if (users.length < virtualUsers) {
  throw new Error(
    `Not enough test accounts: VUS=${virtualUsers}, accounts=${users.length}`,
  );
}

export const options = {
  scenarios: {
    product_excel_jobs: {
      executor: "per-vu-iterations",
      vus: virtualUsers,
      iterations: 1,
      maxDuration: `${jobTimeoutSeconds + 60}s`,
    },
  },
  thresholds: {
    checks: ["rate>0.99"],
    http_req_failed: ["rate<0.01"],
    job_failed: ["rate<0.01"],
  },
};

function parseJson(response, description) {
  try {
    return response.json();
  } catch (error) {
    console.error(`${description}: response is not JSON (${response.status})`);
    return null;
  }
}

function stopWithFailure(message, startedAt) {
  if (startedAt !== undefined) {
    jobDuration.add(Date.now() - startedAt);
  }
  jobFailed.add(true);
  fail(message);
}

export default function () {
  const user = users[__VU - 1];

  const loginResponse = http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({
      email: user.email,
      password: user.password,
    }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { name: "auth_login" },
    },
  );

  const loginSucceeded = check(loginResponse, {
    "login returns 200": (response) => response.status === 200,
    "login response is successful": (response) => {
      try {
        return response.json("success") === true;
      } catch (_) {
        return false;
      }
    },
  });

  if (!loginSucceeded) {
    stopWithFailure(`Login failed for VU ${__VU}: ${loginResponse.status}`);
  }

  const startedAt = Date.now();
  const createResponse = http.post(
    `${baseUrl}/api/v1/product-excel-jobs`,
    {
      file: http.file(
        excelFile,
        `products-vu-${__VU}.xlsx`,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      ),
      includeSelectionDetails: "false",
    },
    { tags: { name: "product_excel_job_create" } },
  );

  const createBody = parseJson(createResponse, "Create job failed");
  const jobId = createBody?.data?.jobId;
  const jobCreated = check(createResponse, {
    "job creation returns 200": (response) => response.status === 200,
    "job creation returns jobId": () => Number.isInteger(jobId),
  });

  if (!jobCreated) {
    stopWithFailure(
      `Create job failed for VU ${__VU}: ${createResponse.status}`,
      startedAt,
    );
  }

  const deadline = Date.now() + jobTimeoutSeconds * 1000;
  let processingStartedAt;

  while (Date.now() < deadline) {
    sleep(pollSeconds);

    const statusResponse = http.get(
      `${baseUrl}/api/v1/product-excel-jobs/${jobId}/status`,
      { tags: { name: "product_excel_job_status" } },
    );

    const statusRequestSucceeded = check(statusResponse, {
      "job status returns 200": (response) => response.status === 200,
    });

    if (!statusRequestSucceeded) {
      stopWithFailure(
        `Status request failed for job ${jobId}: ${statusResponse.status}`,
        startedAt,
      );
    }

    const statusBody = parseJson(statusResponse, "Read job status failed");
    const status = statusBody?.data?.status;

    if (status === "PROCESSING" && processingStartedAt === undefined) {
      processingStartedAt = Date.now();
      jobQueueWait.add(processingStartedAt - startedAt);
    }

    if (status === "COMPLETED") {
      jobDuration.add(Date.now() - startedAt);
      jobFailed.add(false);
      return;
    }

    if (status === "FAILED") {
      stopWithFailure(`Job ${jobId} failed`, startedAt);
    }

    if (!["PENDING", "PROCESSING"].includes(status)) {
      stopWithFailure(`Job ${jobId} returned unknown status: ${status}`, startedAt);
    }
  }

  stopWithFailure(
    `Job ${jobId} timed out after ${jobTimeoutSeconds} seconds`,
    startedAt,
  );
}
