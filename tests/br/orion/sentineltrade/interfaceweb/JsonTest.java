package br.orion.sentineltrade.interfaceweb;

import static org.junit.jupiter.api.Assertions.*;

import br.orion.sentineltrade.dominio.Excecoes.ValidacaoException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonTest {
    private static final Set<String> TUDO = Set.of("a", "b", "c", "n");

    @Test void escritaEscapaCaracteresPerigosParaHtmlEControle() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", "<script>alert(\"1\")</script> & \n\t\u0001 \u2028");
        String j = Json.escrever(m);
        assertFalse(j.contains("<") || j.contains(">") || j.contains("&") || j.contains("\n") || j.contains("\u2028"));
        assertTrue(j.contains("\\u003cscript\\u003e") && j.contains("\\\"1\\\"") && j.contains("\\n"));
    }

    @Test void escritaDeTiposBasicos() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("n", 5); m.put("l", 7L); m.put("b", true); m.put("z", null); m.put("lista", List.of("a", 1));
        assertEquals("{\"n\":5,\"l\":7,\"b\":true,\"z\":null,\"lista\":[\"a\",1]}", Json.escrever(m));
    }

    @Test void leituraAceitaObjetoPlano() {
        Map<String, Object> m = Json.lerObjetoPlano(" { \"a\" : \"texto\\u00e9\\n\", \"b\": 12.50, \"c\": true, \"n\": null } ", TUDO);
        assertEquals("texto\u00e9\n", m.get("a"));
        assertEquals("12.50", Json.texto(m, "b"), "número mantido como texto");
        assertTrue(Json.booleano(m, "c"));
        assertNull(m.get("n"));
        assertTrue(Json.lerObjetoPlano("{}", TUDO).isEmpty());
    }

    @Test void leituraRecusaTudoQueNaoSejaObjetoPlanoEstrito() {
        String[] ruins = {"", "   ", "{", "}", "[]", "null", "\"x\"", "{\"a\":}", "{\"a\":1,}", "{,}", "{\"a\" 1}", "{a:1}", "{'a':1}",
                "{\"a\":{\"b\":1}}", "{\"a\":[1,2]}", "{\"a\":1}{\"b\":2}", "{\"a\":1} x", "{\"a\":1,\"a\":2}", "{\"a\":\"\\q\"}",
                "{\"a\":\"\\u12\"}", "{\"a\":\"nao termina}", "{\"a\":\"quebra\nlinha\"}", "{\"zzz\":1}", "{\"a\":" + "9".repeat(40) + "}",
                "{\"a\":\"" + "x".repeat(600) + "\"}", "{\"" + "k".repeat(70) + "\":1}", "{\"a\":NaN}", "{\"a\":tru}"};
        for (String ruim : ruins) assertThrows(ValidacaoException.class, () -> Json.lerObjetoPlano(ruim, TUDO), "deveria recusar: " + ruim);
        assertThrows(ValidacaoException.class, () -> Json.lerObjetoPlano(null, TUDO));
    }

    @Test void campoForaDaAllowlistEhRecusado() {
        assertThrows(ValidacaoException.class, () -> Json.lerObjetoPlano("{\"a\":1,\"admin\":true}", Set.of("a")));
    }

    @Test void booleanoExigeTrueOuFalse() {
        assertThrows(ValidacaoException.class, () -> Json.booleano(Json.lerObjetoPlano("{\"c\":\"sim\"}", TUDO), "c"));
        assertFalse(Json.booleano(Json.lerObjetoPlano("{}", TUDO), "c"));
    }
}
