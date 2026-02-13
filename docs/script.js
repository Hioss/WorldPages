async function loadDates() {
  const status = document.getElementById("status");
  const select = document.getElementById("date-select");
  status.textContent = "加载日期列表…";

  try {
    const res = await fetch("data/date.json?_=" + Date.now());
    if (!res.ok) throw new Error("date.json 加载失败");
    const data = await res.json();

    const dates = data.dates || [];
    if (dates.length === 0) {
      status.textContent = "暂无数据，请等待 GitHub Actions 首次运行。";
      return;
    }

    // 填充下拉框
    select.innerHTML = "";
    dates.forEach(d => {
      const opt = document.createElement("option");
      opt.value = d;
      opt.textContent = d;
      select.appendChild(opt);
    });

    status.textContent = "";
    // 默认选最新日期
    await loadDataForDate(dates[0]);

    select.addEventListener("change", async () => {
      await loadDataForDate(select.value);
    });
  } catch (e) {
    console.error(e);
    status.textContent = "加载日期失败：" + e.message;
  }
}

async function loadDataForDate(dateStr) {
  const status = document.getElementById("status");
  status.textContent = "加载 " + dateStr + " 的数据…";

  const bbcList = document.getElementById("bbc-list");
  const baiduList = document.getElementById("baidu-list");
  const toutiaoList = document.getElementById("toutiao-list");

  bbcList.innerHTML = "";
  baiduList.innerHTML = "";
  toutiaoList.innerHTML = "";

  try {
    const res = await fetch("data/NewsPage-" + dateStr + ".json?_=" + Date.now());
    if (!res.ok) throw new Error("数据文件不存在");
    const data = await res.json();

    renderList(bbcList, data.BBC中文网热点 || []);
    renderList(baiduList, data.百度热搜 || []);
    renderList(toutiaoList, data.今日头条热榜 || []);

    status.textContent = "已加载 " + dateStr + " 数据。";
  } catch (e) {
    console.error(e);
    status.textContent = "加载失败：" + e.message;
    bbcList.innerHTML = '<div class="empty">暂无数据</div>';
    baiduList.innerHTML = '<div class="empty">暂无数据</div>';
    toutiaoList.innerHTML = '<div class="empty">暂无数据</div>';
  }
}

function renderList(container, arr) {
  if (!arr || arr.length === 0) {
    container.innerHTML = '<div class="empty">暂无数据</div>';
    return;
  }
  container.innerHTML = "";
  arr.forEach((item, idx) => {
    const div = document.createElement("div");
    div.className = "card";
    div.innerHTML = `
      <span class="card-index">${idx + 1}.</span>
      <a href="${item.link}" target="_blank" rel="noopener noreferrer">${escapeHtml(item.title)}</a>
    `;
    container.appendChild(div);
  });
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

document.addEventListener("DOMContentLoaded", loadDates);
