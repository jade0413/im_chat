FROM nginx:1.27-alpine

COPY im-web/README.md /usr/share/nginx/html/README.md
RUN printf '%s\n' \
  '<!doctype html>' \
  '<html lang="zh-CN">' \
  '<head><meta charset="utf-8"><title>im-web placeholder</title></head>' \
  '<body><h1>im-web placeholder</h1><p>Web client is scheduled for a later PR.</p></body>' \
  '</html>' > /usr/share/nginx/html/index.html
