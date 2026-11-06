# Preparação dos testes: deixa o Python achar o código em src/ e lê o .env.
# Os testes que usam banco precisam da variável SENTINEL_TEST_DATABASE_URL (no .env).
import logging
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))

from sentineltrade.api import ler_env  # noqa: E402

ler_env()
URL_TESTE = os.environ.get("SENTINEL_TEST_DATABASE_URL")
SENHA = "Sentinel@2026"  # senha fictícia dos usuários de exemplo

# durante os testes os avisos do log não aparecem na tela (alguns testes erram a senha de propósito)
logging.disable(logging.CRITICAL)