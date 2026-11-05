# Para rodar:  python main.py   e abrir  http://127.0.0.1:8000/docs
import logging
import os
import sys

import uvicorn

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "src"))

from sentineltrade.api import Sistema, criar_api, ler_env  # noqa: E402

ler_env()
os.makedirs("logs", exist_ok=True)
# log com data, nível e mensagem (senhas, tokens e códigos nunca são registrados)
logging.basicConfig(filename="logs/sentineltrade.log", level=logging.INFO,
                    format="%(asctime)s %(levelname)s %(message)s")

url = os.environ.get("SENTINEL_DATABASE_URL")
if not url:
    print("Configure a SENTINEL_DATABASE_URL no arquivo .env (veja o README).")
else:
    print("SentinelTrade rodando. Abra http://127.0.0.1:8000/docs no navegador.")
    uvicorn.run(criar_api(Sistema(url)), host="127.0.0.1", port=8000)