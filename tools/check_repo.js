const https = require("https");

function get(path) {
  return new Promise((resolve) => {
    https
      .get(
        {
          host: "api.github.com",
          path,
          headers: { "User-Agent": "x", Accept: "application/vnd.github+json" },
        },
        (res) => {
          let d = "";
          res.on("data", (c) => (d += c));
          res.on("end", () => resolve([res.statusCode, d]));
        }
      )
      .on("error", (e) => resolve([0, e.message]));
  });
}

const REPO = "/repos/gterryd/naiwa-release";

(async () => {
  const [s, d] = await get(REPO);
  if (s !== 200) {
    console.log("查询失败", s);
    return;
  }
  const j = JSON.parse(d);
  console.log("=== 仓库 ===");
  console.log("  名称:", j.full_name, "| 可见性:", j.private ? "私有" : "公开");
  console.log("  描述:", j.description || "(还没设)");
  console.log("  Topics:", (j.topics || []).join(", ") || "(还没设)");
  console.log("  License:", j.license ? j.license.spdx_id : "(未识别)");

  const [s2, d2] = await get(REPO + "/commits/main");
  if (s2 === 200) {
    const c = JSON.parse(d2);
    console.log("  最新提交:", c.sha.slice(0, 7), c.commit.message.split("\n")[0]);
  }

  const [s3, d3] = await get(REPO + "/contents/app/build.gradle.kts");
  if (s3 === 200) {
    const txt = Buffer.from(JSON.parse(d3).content, "base64").toString();
    const ver = txt.match(/versionName = "([^"]+)"/);
    const repo = txt.match(/UPDATE_REPO[^,]*,\s*"\\"([^\\]+)\\""/);
    console.log("  线上代码版本号:", ver ? ver[1] : "?");
    console.log("  线上更新地址:", repo ? repo[1] : "?");
  }

  const [s4, d4] = await get(REPO + "/releases/latest");
  if (s4 === 200) {
    const r = JSON.parse(d4);
    console.log("");
    console.log("=== 最新 Release ===");
    console.log("  " + r.tag_name + " | 附件: " + (r.assets || []).map((a) => a.name).join(", "));
  }
})();
