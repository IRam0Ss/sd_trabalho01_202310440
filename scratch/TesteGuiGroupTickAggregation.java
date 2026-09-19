import java.util.*;

public class TesteGuiGroupTickAggregation {
  public static void main(String[] args) {
    System.out.println("==========================================================");
    System.out.println(" TESTE: AGREGACAO DE CONFIRMACAO DE LEITURA EM GRUPO     ");
    System.out.println("   (Verde Neon APENAS quando TODOS os membros lerem)      ");
    System.out.println("==========================================================");

    String idMensagem = UUID.randomUUID().toString();
    Set<String> expectedMembers = new HashSet<>(Arrays.asList("bob", "carol"));
    Set<String> readUsers = new HashSet<>();
    Set<String> deliveredUsers = new HashSet<>();

    // Estado inicial: Mensagem enviada ao servidor
    int statusSimulado = 1;
    String corAtual = obterCorTick(readUsers, deliveredUsers, expectedMembers, statusSimulado);
    System.out.println("[1] Apos envio ao servidor: Cor=" + corAtual);
    if (!corAtual.equals("ChegouServidor")) {
      throw new RuntimeException("Falha no status 1");
    }

    // Bob recebe a mensagem (Status 2)
    deliveredUsers.add("bob");
    statusSimulado = 2;
    corAtual = obterCorTick(readUsers, deliveredUsers, expectedMembers, statusSimulado);
    System.out.println("[2] Apos Bob receber (Status 2): Cor=" + corAtual);
    if (!corAtual.equals("LimaEDEN_Entregue")) {
      throw new RuntimeException("Falha no status 2");
    }

    // Apenas Bob visualiza a mensagem (VU ou normal) -> Status 3 de Bob
    readUsers.add("bob");
    statusSimulado = 3;
    corAtual = obterCorTick(readUsers, deliveredUsers, expectedMembers, statusSimulado);
    System.out.println("[3] Apenas Bob visualizou (1/2 leram): Cor=" + corAtual);
    if (!corAtual.equals("LimaEDEN_Entregue")) {
      throw new RuntimeException("FALHA: Nao deve ficar Verde Neon quando apenas 1 pessoa leu!");
    }
    System.out.println("    >>> SUCESSO: Cor continuou em Lima EDEN (nao marcou como todos lido).");

    // Agora Carol tambem visualiza a mensagem -> Status 3 de Carol
    readUsers.add("carol");
    corAtual = obterCorTick(readUsers, deliveredUsers, expectedMembers, statusSimulado);
    System.out.println("[4] Carol tambem visualizou (2/2 leram): Cor=" + corAtual);
    if (!corAtual.equals("VerdeNeon_TodosLeram")) {
      throw new RuntimeException("FALHA: Deveria ficar Verde Neon pois TODOS leram!");
    }
    System.out.println("    >>> SUCESSO: Cor mudou para Verde Neon apos TODOS lerem!");

    System.out.println("\n==========================================================");
    System.out.println(" TESTE DE AGREGACAO DE TICKS PASSOU COM 100% DE SUCESSO!  ");
    System.out.println("==========================================================");
  }

  private static String obterCorTick(Set<String> readUsers, Set<String> deliveredUsers, Set<String> expectedMembers, int status) {
    boolean todosLeram = !expectedMembers.isEmpty() && readUsers.containsAll(expectedMembers);
    boolean algumEntregue = !deliveredUsers.isEmpty() || !readUsers.isEmpty();

    if (todosLeram) {
      return "VerdeNeon_TodosLeram";
    } else if (algumEntregue) {
      return "LimaEDEN_Entregue";
    } else {
      return "ChegouServidor";
    }
  }
}

