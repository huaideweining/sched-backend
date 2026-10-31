/**
 * 工作台静态服务器（局域网共享用）
 *
 * 作用：在"后端所在电脑"上启动一个绑定 0.0.0.0 的静态服务器，把工作台页面托管出去，
 *       使同一局域网内的其他 PC 能用 http://<本机IP>:<端口>/个人办公工作台.html 直接访问。
 *       页面内 API_BASE 已改为"跟随页面主机 + 18077"，无需手动 ?api=。
 *
 * 规范位置：本文件与 个人办公工作台.html / index.html 一同纳入 sched-backend 仓库的
 *           wbpage/ 目录，作为"唯一可信源"。ROOT 默认 = 本脚本所在目录(__dirname)，
 *           即仓库 wbpage/，因此无需任何符号链接即可直接服务仓库内的页面。
 *
 * 用法：
 *   node serve.js            # 默认：端口 8088，托管本脚本所在目录(仓库 wbpage/)
 *   node serve.js 9000       # 指定端口
 *   node serve.js 8088 D:/web # 指定端口 + 根目录(覆盖默认)
 *
 * 配套：后端 sched-backend 必须已启动（端口 18077），且本机防火墙放行本端口（入站 TCP）。
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');

const PORT = Number(process.argv[2]) || 8088;
const ROOT = path.resolve(process.argv[3] || __dirname);

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.htm': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.txt': 'text/plain; charset=utf-8',
};

function lanIPv4() {
  const out = [];
  const ifaces = os.networkInterfaces();
  for (const name of Object.keys(ifaces)) {
    for (const info of ifaces[name] || []) {
      if (info.family === 'IPv4' && !info.internal) out.push(info.address);
    }
  }
  return out;
}

const server = http.createServer((req, res) => {
  try {
    let urlPath = decodeURIComponent(req.url.split('?')[0]);
    if (urlPath === '/') urlPath = '/index.html';

    const safePath = path.normalize(path.join(ROOT, urlPath));
    if (safePath !== ROOT && !safePath.startsWith(ROOT + path.sep) && !safePath.startsWith(path.normalize(ROOT))) {
      res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('403 Forbidden');
      return;
    }

    fs.stat(safePath, (err, stat) => {
      if (err) {
        res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('404 Not Found');
        return;
      }
      if (stat.isDirectory()) {
        fs.readdir(safePath, (e, files) => {
          if (e) { res.writeHead(404); res.end('Not found'); return; }
          res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
          const items = files
            .map((f) => `<li><a href="${encodeURIComponent(f)}">${f}</a></li>`)
            .join('');
          res.end(`<h3>${safePath}</h3><ul>${items}</ul>`);
        });
        return;
      }
      const ext = path.extname(safePath).toLowerCase();
      res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' });
      fs.createReadStream(safePath).pipe(res);
    });
  } catch (e) {
    res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('500 Internal Error');
  }
});

server.listen(PORT, '0.0.0.0', () => {
  const ips = lanIPv4();
  console.log(`\n[工作台静态服务器] 已启动，监听 0.0.0.0:${PORT}`);
  console.log(`  根目录: ${ROOT}`);
  console.log(`  本机访问 : http://127.0.0.1:${PORT}/个人办公工作台.html`);
  if (ips.length) {
    console.log(`  局域网访问: ${ips.map((ip) => `http://${ip}:${PORT}/个人办公工作台.html`).join('  ')}`);
  } else {
    console.log('  (未检测到局域网 IPv4，请确认网卡已连接)');
  }
  console.log('  按 Ctrl+C 停止。\n');
});
