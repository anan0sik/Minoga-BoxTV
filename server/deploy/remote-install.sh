#!/usr/bin/env bash
# Runs on Server1. Expects files uploaded to /tmp/minogatvbox-deploy
set -euo pipefail
SRC=/tmp/minogatvbox-deploy
DOMAIN=minogatvbox.duckdns.org
SITE=/etc/nginx/sites-available/$DOMAIN

if ! command -v java >/dev/null 2>&1; then
  sudo apt-get update -y && sudo apt-get install -y openjdk-17-jre-headless
fi
if ! command -v nginx >/dev/null 2>&1; then
  sudo apt-get install -y nginx
fi

sudo mkdir -p /opt/minogatvbox/web /opt/minogatvbox/server /var/www/minogatvbox/download
sudo cp "$SRC"/IptvMockServer*.class /opt/minogatvbox/server/
sudo cp "$SRC"/index.html "$SRC"/config.json /opt/minogatvbox/web/
sudo chown -R www-data:www-data /opt/minogatvbox

sudo cp "$SRC"/minogatvbox.service /etc/systemd/system/minogatvbox.service
sudo systemctl daemon-reload
sudo systemctl enable --now minogatvbox
sudo systemctl restart minogatvbox

# Nginx: shared locations snippet + site config (HTTPS variant if certificate already exists)
sudo cp "$SRC"/minogatvbox-common.conf /etc/nginx/snippets/minogatvbox-common.conf
if sudo test -f /etc/letsencrypt/live/$DOMAIN/fullchain.pem; then
  sudo cp "$SRC"/minogatvbox.nginx.ssl.conf "$SITE"
  HAVE_CERT=1
else
  sudo cp "$SRC"/minogatvbox.nginx.conf "$SITE"
  HAVE_CERT=0
fi
sudo ln -sf "$SITE" /etc/nginx/sites-enabled/$DOMAIN
sudo nginx -t && sudo systemctl reload nginx

# Open port 80/443 in host firewall (Oracle Cloud images use iptables)
for p in 80 443; do
  sudo iptables -C INPUT -p tcp --dport $p -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 5 -p tcp --dport $p -j ACCEPT
done
command -v netfilter-persistent >/dev/null 2>&1 && sudo netfilter-persistent save || true

# First run: obtain certificate, then switch to the HTTPS config
if [ "$HAVE_CERT" = "0" ]; then
  command -v certbot >/dev/null 2>&1 || sudo apt-get install -y certbot python3-certbot-nginx
  if sudo certbot certonly --webroot -w /var/www/html -d $DOMAIN --non-interactive --agree-tos --register-unsafely-without-email; then
    sudo test -f /etc/letsencrypt/options-ssl-nginx.conf || sudo certbot --nginx -d $DOMAIN --non-interactive --agree-tos --register-unsafely-without-email
    sudo cp "$SRC"/minogatvbox.nginx.ssl.conf "$SITE"
    sudo nginx -t && sudo systemctl reload nginx
  else
    echo "certbot failed (HTTP still works)"
  fi
fi

sleep 2
curl -fsS http://127.0.0.1:8091/health && echo " <- backend OK"
