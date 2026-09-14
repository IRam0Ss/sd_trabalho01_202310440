/**
 * Autor: Iury Ramos Sodre
 * Matricula: 202310440
 * Inicio: 15/06/2026
 * Ultima alteracao: 14/09/2026
 * Nome: GerenciadorGrupos
 * Funcao: Centraliza a gestao de salas/grupos, mapeamento de usuarios ativos e broadcasting de eventos.
 */

package controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import utils.InfoUser;
import Protocol.APDU;

/**
 * Componente central do servidor responsavel pelo controle e sincronizacao de grupos e usuarios ativos.
 * Emprega operacoes sincronizadas (thread-safe) para garantir consistencia na concorrencia de multiplos clientes.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
@SuppressWarnings("unused")
public class GerenciadorGrupos {

  private Map<String, List<InfoUser>> gruposExistentes;
  private java.util.Set<InfoUser> todosUsuariosAtivos;

  /**
   * Construtor padrao. Inicializa a estrutura de dados.
   */
  public GerenciadorGrupos() {
    gruposExistentes = new HashMap<>();
    todosUsuariosAtivos = new java.util.HashSet<>();
  }
  
  private byte[] serializarAPDU(Protocol.APDU apdu) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeUnshared(apdu);
      oos.flush();
      return baos.toByteArray();
    } catch (Exception e) {
      System.err.println("[GERENCIADOR] [ERROR] Falha ao serializar APDU: " + e.getMessage());
      return new byte[0];
    }
  }

  /**
   * Adiciona um usuario a um grupo. Se o grupo nao existir, ele e criado
   * automaticamente.
   * 
   * @param nomeGrupo Nome do grupo desejado
   * @param usuario   Objeto InfoUser contendo os dados do usuario
   * @return true se o usuario foi adicionado com sucesso, false se ele ja estava
   *         no grupo
   */
  public synchronized boolean join(String nomeGrupo, InfoUser usuario) {

    if (!gruposExistentes.containsKey(nomeGrupo)) { // caso o grupo nao exista, criar o grupo e adicionar o membro
      gruposExistentes.put(nomeGrupo, new ArrayList<>());
      System.out.println("[GERENCIADOR] [INFO] Grupo '" + nomeGrupo + "' criado");
    }

    List<InfoUser> membros = gruposExistentes.get(nomeGrupo); // lista todos os membros do grupo

    if (membros.contains(usuario)) {
      System.out.println("[GERENCIADOR] [WARNING] Usuario '" + usuario.getNome() + "' tentou entrar no grupo '"
          + nomeGrupo + "' mas ja e membro");
      return false;
    }
    membros.add(usuario); // adiciona o novo membro
    System.out.println("[GERENCIADOR] [INFO] Usuario '" + usuario.getNome() + "' entrou no grupo '" + nomeGrupo
        + "'. Total de membros: " + membros.size());
    return true;
  } // end join

  /**
   * Remove um usuario de um grupo. Se o grupo ficar vazio apos a remocao, ele e
   * deletado
   * para evitar acumulo de grupos fantasmas na memoria.
   * 
   * @param nomeGrupo Nome do grupo
   * @param usuario   Objeto InfoUser contendo os dados do usuario
   * @return true se removido com sucesso, false se o grupo nao existir ou usuario
   *         nao pertencer a ele
   */
  public synchronized boolean leave(String nomeGrupo, InfoUser usuario) {
    if (!gruposExistentes.containsKey(nomeGrupo)) {
      System.out.println("[GERENCIADOR] [WARNING] Tentativa de LEAVE em grupo inexistente: '" + nomeGrupo + "'");
      return false;
    }
    List<InfoUser> membros = gruposExistentes.get(nomeGrupo);

    if (!membros.contains(usuario)) {
      System.out.println("[GERENCIADOR] [WARNING] Usuario '" + usuario.getNome() + "' tentou sair do grupo '"
          + nomeGrupo + "' sem ser membro");
      return false;
    }
    membros.remove(usuario);
    System.out.println("[GERENCIADOR] [INFO] Usuario '" + usuario.getNome() + "' saiu do grupo '" + nomeGrupo
        + "'. Total de membros: " + membros.size());

    if (membros.isEmpty()) { // se todos os membros sairam, deleta o grupo
      gruposExistentes.remove(nomeGrupo);
      System.out.println("[GERENCIADOR] [INFO] Grupo '" + nomeGrupo + "' deletado da memoria pois ficou vazio");
    }
    return true;
  } // fim do leave

  /**
   * Retorna a lista de membros de um grupo, excluindo o remetente.
   * E utilizado na operacao SEND para encaminhar a mensagem para todos os membros
   * corretos.
   * 
   * @param nomeGrupo        Nome do grupo
   * @param usuarioRemetente Usuario que esta enviando a mensagem
   * @return Lista de usuarios destinatarios
   */
  public synchronized List<InfoUser> getMembrosEnvio(String nomeGrupo, InfoUser usuarioRemetente) {

    if (!gruposExistentes.containsKey(nomeGrupo)) {
      System.out.println("[GERENCIADOR] [WARNING] Tentativa de envio para grupo inexistente: '" + nomeGrupo + "'");
      return new ArrayList<>();
    }

    List<InfoUser> listaMembros = new ArrayList<>(gruposExistentes.get(nomeGrupo));

    if (!listaMembros.contains(usuarioRemetente)) {
      System.out.println("[GERENCIADOR] [WARNING] Bloqueado: Usuario '" + usuarioRemetente.getNome()
          + "' tentou enviar mensagem para o grupo '" + nomeGrupo + "' sem ser membro");
      return new ArrayList<>();
    }

    listaMembros.remove(usuarioRemetente);
    System.out.println("[GERENCIADOR] [INFO] Mensagem de '" + usuarioRemetente.getNome() + "' autorizada para "
        + listaMembros.size() + " destinatario(s) no grupo '" + nomeGrupo + "'");
    return listaMembros;
  } // fim do getMembrosEnvio

  /**
   * Retorna a lista de todos os membros de um grupo, sem excecao.
   * 
   * @param nomeGrupo Nome do grupo
   * @return Lista de usuarios pertencentes ao grupo
   */
  public synchronized List<InfoUser> getTodosMembros(String nomeGrupo) {
    if (!gruposExistentes.containsKey(nomeGrupo)) {
      return new ArrayList<>();
    }
    return new ArrayList<>(gruposExistentes.get(nomeGrupo));
  }

  /**
   * Retorna a lista de nomes dos grupos atualmente ativos.
   * 
   * @return Lista de strings contendo os nomes dos grupos
   */
  public synchronized List<String> listarGrupos() {
    List<String> grupos = new ArrayList<>();
    for (String g : gruposExistentes.keySet()) {
      if (g != null && !g.startsWith("@")) {
        grupos.add(g);
      }
    }
    return grupos;
  }

  /**
   * Imprime o estado atual de todos os grupos e seus respectivos membros no
   * console.
   */
  public synchronized void imprimirEstado() {
    System.out.println("\nESTADO DA ED ");
    for (Map.Entry<String, List<InfoUser>> entry : gruposExistentes.entrySet()) {
      System.out.println("  [" + entry.getKey() + "] → " + entry.getValue());
    }
    System.out.println("\n");
  } // fim imprimirEstado

  /**
   * Registra um usuario globalmente no servidor, caso o nome nao esteja em uso.
   * 
   * @param usuario Usuario a ser registrado
   * @return true se registrado com sucesso, false se o nome ja estiver em uso
   */
  public synchronized boolean registrarUsuario(InfoUser usuario) {
    System.out.println("[GERENCIADOR] [DEBUG] Tentando registrar usuario: " + usuario.toString());
    System.out.println("[GERENCIADOR] [DEBUG] Usuarios ativos antes do registro: " + todosUsuariosAtivos.size());
    for (InfoUser u : todosUsuariosAtivos) {
      System.out.println("[GERENCIADOR] [DEBUG]   -> " + u.toString());
      if (u.getNome().equalsIgnoreCase(usuario.getNome())) {
        if (u.getIp().equals(usuario.getIp())) {
          todosUsuariosAtivos.remove(u);
          todosUsuariosAtivos.add(usuario);
          System.out.println("[GERENCIADOR] [INFO] Usuario '" + usuario.getNome() + "' atualizou sua sessao (Porta UDP: " + usuario.getPorta() + ").");
          return true;
        }
        System.out.println("[GERENCIADOR] [WARNING] Nome '" + usuario.getNome() + "' ja esta em uso.");
        return false;
      }
    }
    todosUsuariosAtivos.add(usuario);
    System.out.println("[GERENCIADOR] [INFO] Usuario '" + usuario.getNome() + "' registrado com sucesso. Total ativos: "
        + todosUsuariosAtivos.size());
    return true;
  }

  /**
   * Remove um usuario da lista global do servidor.
   * 
   * @param usuario Usuario a ser removido
   */
  public synchronized void removerUsuario(InfoUser usuario) {
    todosUsuariosAtivos.remove(usuario);
  }

  /**
   * Retorna todos os usuarios conectados atualmente ao servidor.
   * 
   * @return Conjunto de InfoUser com todos os usuarios ativos
   */
  public synchronized java.util.Set<InfoUser> getTodosUsuariosAtivos() {
    return new java.util.HashSet<>(todosUsuariosAtivos);
  }

  /**
   * Busca um usuario conectado pelo nome.
   * 
   * @param nome Nome do usuario a buscar
   * @return InfoUser correspondente, ou null se nao encontrado
   */
  public synchronized InfoUser buscarUsuarioPorNome(String nome) {
    if (nome == null) {
      return null;
    }
    String busca = nome.trim();
    if (busca.startsWith("@")) {
      busca = busca.substring(1).trim();
    }
    for (InfoUser u : todosUsuariosAtivos) {
      if (u.getNome().trim().equalsIgnoreCase(busca)) {
        return u;
      }
    }
    return null;
  }

  /**
   * Notifica todos os usuarios ativos via UDP que a lista de usuarios online
   * mudou.
   */
  public void notificarAtualizacaoUsuarios() {
    new Thread(() -> {
      try (java.net.DatagramSocket socketUDP = new java.net.DatagramSocket()) {
        Protocol.APDU apdu = new Protocol.APDU(utils.Protocolo.UPDATE_USERS, null, null, null, 0);
        byte[] dados = serializarAPDU(apdu);
        
        List<InfoUser> ativos;
        synchronized (this) {
          ativos = new ArrayList<>(todosUsuariosAtivos);
        }
        for (InfoUser u : ativos) {
          try {
            java.net.InetAddress ipDest = java.net.InetAddress.getByName(u.getIp());
            java.net.DatagramPacket pacote = new java.net.DatagramPacket(dados, dados.length, ipDest, u.getPorta());
            socketUDP.send(pacote);
          } catch (Exception e) {
          }
        }
        System.out
            .println("[GERENCIADOR] [INFO] Notificacao UPDATE_USERS disparada para " + ativos.size() + " clientes.");
      } catch (Exception e) {
        System.err.println("[GERENCIADOR] [ERROR] Falha ao notificar atualizacao: " + e.getMessage());
      }
    }).start();
  }

  /**
   * Notifica todos os membros de um grupo com uma mensagem do sistema.
   * Utilizado para avisar sobre ~JOINED~ e ~LEFT~.
   */
  public void notificarMensagemSistema(String nomeGrupo, InfoUser remetenteVirtual, String mensagem) {
    new Thread(() -> {
      try (java.net.DatagramSocket socketUDP = new java.net.DatagramSocket()) {
        Protocol.APDU apdu = new Protocol.APDU(utils.Protocolo.SEND, nomeGrupo, remetenteVirtual.getNome(), mensagem, remetenteVirtual.getPorta());
        byte[] dados = serializarAPDU(apdu);

        List<InfoUser> membros;
        synchronized (this) {
          if (!gruposExistentes.containsKey(nomeGrupo))
            return;
          membros = new ArrayList<>(gruposExistentes.get(nomeGrupo));
        }

        // Nao removemos o remetenteVirtual pois queremos que ele mesmo veja a msg?
        // Na verdade o Cliente que enviou ja mostra na tela localmente (addChatBubble).
        // Entao removemos o remetente para ele nao receber de volta se ele ainda
        // estiver na lista.
        membros.remove(remetenteVirtual);

        for (InfoUser u : membros) {
          try {
            java.net.InetAddress ipDest = java.net.InetAddress.getByName(u.getIp());
            java.net.DatagramPacket pacote = new java.net.DatagramPacket(dados, dados.length, ipDest, u.getPorta());
            socketUDP.send(pacote);
          } catch (Exception e) {
          }
        }
        System.out.println(
            "[GERENCIADOR] [INFO] Mensagem de sistema (" + mensagem + ") disparada para o grupo '" + nomeGrupo + "'.");
      } catch (Exception e) {
        System.err.println("[GERENCIADOR] [ERROR] Falha ao notificar mensagem de sistema: " + e.getMessage());
      }
    }).start();
  }

}// fim da class
