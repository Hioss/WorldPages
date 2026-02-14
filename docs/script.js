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

    select.innerHTML = "";
    dates.forEach(d => {
      const opt = document.createElement("option");
      opt.value = d;
      opt.textContent = d;
      select.appendChild(opt);
    });

    status.textContent = "";
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
  const columnsEl = document.getElementById("columns");
  status.textContent = "加载 " + dateStr + " 的数据…";
  columnsEl.innerHTML = "";

  try {
    const res = await fetch("data/NewsPage-" + dateStr + ".json?_=" + Date.now());
    if (!res.ok) throw new Error("数据文件不存在");
    const data = await res.json();

    // 动态栏目：拿到所有 key，排除 date
    const categories = Object.keys(data).filter(k => k !== "date");

    if (categories.length === 0) {
      columnsEl.innerHTML = `<div class="empty">该日期文件没有任何栏目</div>`;
      status.textContent = "已加载 " + dateStr + " 数据（无栏目）。";
      return;
    }

    // 逐个生成 column
    categories.forEach((catName) => {
      const column = document.createElement("div");
      column.className = "column";

      const h2 = document.createElement("h2");
      h2.textContent = catName;

      // 手机端折叠：点标题折叠/展开
      h2.addEventListener("click", () => {
        column.classList.toggle("collapsed");
      });

      const list = document.createElement("div");
      list.className = "card-list";

      column.appendChild(h2);
      column.appendChild(list);
      columnsEl.appendChild(column);

      renderList(list, data[catName] || []);
    });

    status.textContent = "已加载 " + dateStr + " 数据。";
  } catch (e) {
    console.error(e);
    status.textContent = "加载失败：" + e.message;
    columnsEl.innerHTML = `<div class="empty">暂无数据</div>`;
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
