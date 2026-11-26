package br.orion.sentineltrade.aplicacao.servico;

import br.orion.sentineltrade.dominio.Dinheiro;
import br.orion.sentineltrade.dominio.Excecoes.ValidacaoException;
import br.orion.sentineltrade.dominio.Lado;
import br.orion.sentineltrade.dominio.Ticker;
import br.orion.sentineltrade.dominio.TipoOrdem;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validação de entradas no servidor por ALLOWLIST (formato, tamanho, tipo, intervalo).
 * Mensagens de erro não ecoam o valor recebido.
 */
public final class Validacoes {
    private Validacoes() {}

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,180}\\.[A-Za-z]{2,24}$");
    private static final Pattern NOME = Pattern.compile("^[\\p{L}][\\p{L} .'-]{1,99}$");
    private static final Pattern QUANTIDADE = Pattern.compile("^[0-9]{1,7}$");
    private static final Pattern PRECO = Pattern.compile("^[0-9]{1,7}([.,][0-9]{1,2})?$");
    private static final Pattern CHAVE = Pattern.compile("^[A-Za-z0-9_-]{16,64}$");
    private static final Pattern CODIGO_MFA = Pattern.compile("^[0-9]{6}$");
    private static final Set<String> SENHAS_COMUNS = Set.of("senha@123456", "password@1234", "qwerty@123456", "admin@123456");

    public static final int QUANTIDADE_MAXIMA = 1_000_000;
    public static final BigDecimal PRECO_MAXIMO = new BigDecimal("1000000.00");

    /** {@code preco} é nulo em ordem a MERCADO (o preço de proteção é calculado pelo serviço). */
    public record OrdemValidada(Lado lado, TipoOrdem tipo, Ticker ticker, int quantidade, Dinheiro preco, String chave, boolean confirmada) {
        /** Identifica o CONTEÚDO da ordem (não inclui a flag de confirmação). Detecta reuso indevido da chave de idempotência. */
        public String impressaoDigital() {
            return Hashes.sha256Hex(lado + "|" + tipo + "|" + ticker + "|" + quantidade + "|" + (preco == null ? "MERCADO" : preco));
        }
        public OrdemValidada comPreco(Dinheiro novoPreco) { return new OrdemValidada(lado, tipo, ticker, quantidade, novoPreco, chave, confirmada); }
    }

    public static boolean emailValido(String email) {
        return email != null && email.length() <= 254 && EMAIL.matcher(email).matches();
    }

    public static String email(String bruto) {
        String e = bruto == null ? "" : bruto.strip().toLowerCase(Locale.ROOT);
        if (!emailValido(e)) throw new ValidacaoException("E-mail inválido");
        return e;
    }

    public static String nome(String bruto) {
        String n = bruto == null ? "" : bruto.strip();
        if (!NOME.matcher(n).matches()) throw new ValidacaoException("Nome inválido (2 a 100 letras, espaços, ponto, apóstrofo ou hífen)");
        return n;
    }

    /** Política: 12–128 caracteres, com maiúscula, minúscula, dígito e símbolo; fora de lista de senhas comuns. */
    public static void senha(String senha, String email) {
        if (senha == null || senha.length() < 12 || senha.length() > 128)
            throw new ValidacaoException("Senha deve ter entre 12 e 128 caracteres");
        boolean up = false, lo = false, di = false, sy = false;
        for (char c : senha.toCharArray()) {
            if (Character.isUpperCase(c)) up = true;
            else if (Character.isLowerCase(c)) lo = true;
            else if (Character.isDigit(c)) di = true;
            else if (!Character.isWhitespace(c)) sy = true;
        }
        if (!(up && lo && di && sy))
            throw new ValidacaoException("Senha deve conter maiúscula, minúscula, número e símbolo");
        if (SENHAS_COMUNS.contains(senha.toLowerCase(Locale.ROOT)))
            throw new ValidacaoException("Senha muito comum");
        if (email != null && email.contains("@") && senha.toLowerCase(Locale.ROOT).contains(email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT)))
            throw new ValidacaoException("Senha não pode conter o identificador do e-mail");
    }

    public static boolean codigoMfaValido(String codigo) { return codigo != null && CODIGO_MFA.matcher(codigo).matches(); }

    public static Lado lado(String bruto) {
        String l = bruto == null ? "" : bruto.strip().toUpperCase(Locale.ROOT);
        return switch (l) {
            case "COMPRA" -> Lado.COMPRA;
            case "VENDA" -> Lado.VENDA;
            default -> throw new ValidacaoException("Tipo de ordem inválido (use COMPRA ou VENDA)");
        };
    }

    public static TipoOrdem tipoOrdem(String bruto) {
        String t = bruto == null || bruto.isBlank() ? "LIMITE" : bruto.strip().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "LIMITE" -> TipoOrdem.LIMITE;
            case "MERCADO" -> TipoOrdem.MERCADO;
            default -> throw new ValidacaoException("Tipo de execução inválido (use LIMITE ou MERCADO)");
        };
    }

    public static int quantidade(String bruto) {
        String q = bruto == null ? "" : bruto.strip();
        if (!QUANTIDADE.matcher(q).matches()) throw new ValidacaoException("Quantidade inválida (inteiro positivo)");
        int v = Integer.parseInt(q);
        if (v < 1 || v > QUANTIDADE_MAXIMA) throw new ValidacaoException("Quantidade fora do intervalo permitido (1 a " + QUANTIDADE_MAXIMA + ")");
        return v;
    }

    public static Dinheiro preco(String bruto) {
        String p = bruto == null ? "" : bruto.strip();
        if (!PRECO.matcher(p).matches()) throw new ValidacaoException("Preço inválido (até 2 casas decimais)");
        BigDecimal v = new BigDecimal(p.replace(',', '.'));
        if (v.signum() <= 0 || v.compareTo(PRECO_MAXIMO) > 0) throw new ValidacaoException("Preço fora do intervalo permitido");
        return Dinheiro.de(v);
    }

    public static String chaveIdempotencia(String bruto) {
        String c = bruto == null ? "" : bruto.strip();
        if (!CHAVE.matcher(c).matches()) throw new ValidacaoException("Chave de idempotência inválida (16 a 64 caracteres: letras, números, '-' ou '_')");
        return c;
    }

    public static UUID uuid(String bruto) {
        try { return UUID.fromString(bruto == null ? "" : bruto.strip()); }
        catch (IllegalArgumentException e) { throw new ValidacaoException("Identificador inválido"); }
    }

    public static OrdemValidada ordem(SolicitacaoOrdem s) {
        if (s == null) throw new ValidacaoException("Solicitação obrigatória");
        TipoOrdem tipo = tipoOrdem(s.tipo());
        boolean semPreco = s.preco() == null || s.preco().isBlank();
        if (tipo == TipoOrdem.MERCADO && !semPreco) throw new ValidacaoException("Ordem a mercado não aceita preço");
        return new OrdemValidada(lado(s.lado()), tipo, Ticker.de(s.ticker()), quantidade(s.quantidade()),
                tipo == TipoOrdem.MERCADO ? null : preco(s.preco()), chaveIdempotencia(s.chaveIdempotencia()), s.confirmada());
    }
}
