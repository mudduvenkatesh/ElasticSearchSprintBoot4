#!/bin/bash
echo "Starting Local ES Infrastructure"
if [[ $# -eq 0 ]]; then
  docker compose -f ../docker/docker-compose.yml -p es-network up -d
elif [ -n "$1" ]; then
  docker compose -f ../docker/docker-compose.yml -p es-network up $1 -d
fi
    
echo "Local ES startup completed successfully"
#echo "unseal vault"
#export VAULT_ADDR="http://127.0.0.1:8200"
#URL=$VAULT_ADDR
#echo "Unsealing #1"
#KEY1=O7UGAaCrSAlGxYpfSoorrJt9IkE51SD0XEmZGo25XJA=
#curl -s --request PUT --data "{\"key\": \"$KEY1\"}" $URL/v1/sys/unseal
