FROM nginx:1.27-alpine

COPY frontend/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY frontend/static /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=10 \
  CMD wget -q -O /dev/null http://localhost/ || exit 1
