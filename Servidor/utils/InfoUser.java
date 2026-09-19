/*****************************************************************
* Autor..............: Iury Ramos Sodre
* Matricula..........: 202310440
* Inicio.............: 15/06/2026
* Ultima alteracao...: 18/09/2026
* Nome...............: InfoUser
* Funcao.............: Entidade de dados para representacao e identificacao unica de usuarios conectados.
*************************************************************** */

package utils;

import java.util.Objects;
import java.net.URLEncoder;
import java.net.URLDecoder;

/**
 * Classe responsavel por armazenar e gerenciar os metadados dos usuarios conectados.
 * A unicidade e a identidade de um usuario sao baseadas na combinacao de seu endereco IP e nome.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class InfoUser {

  private String nome;
  private String ip;
  private int porta;

  /**
   * Construtor padrao da entidade InfoUser.
   * 
   * @param nome  Nome de exibicao/identificacao do usuario.
   * @param ip    Endereco IP de rede do cliente.
   * @param porta Porta UDP onde o cliente escuta mensagens e notificacoes.
   */
  public InfoUser(String nome, String ip, int porta) {
    this.nome = nome;
    this.ip = ip;
    this.porta = porta;
  }

  /**
   * Obtem o nome do usuario.
   * 
   * @return O nome do usuario.
   */
  public String getNome() {
    return nome;
  }

  /**
   * Obtem o endereco IP do usuario.
   * 
   * @return O endereco IP em formato String.
   */
  public String getIp() {
    return ip;
  }

  /**
   * Obtem a porta UDP onde o usuario recebe mensagens.
   * 
   * @return O numero da porta UDP.
   */
  public int getPorta() {
    return porta;
  }

  /**
   * Atualiza o endereco IP do usuario (usado para atualizacao dinamica de transporte).
   * 
   * @param ip Novo endereco IP.
   */
  public void setIp(String ip) {
    this.ip = ip;
  }

  /**
   * Atualiza a porta UDP do usuario.
   * 
   * @param porta Novo numero de porta.
   */
  public void setPorta(int porta) {
    this.porta = porta;
  }

  /**
   * Empacota o objeto em uma string delimitada ("nome;ip;porta").
   * Utilizado para serializacao textual legada e compatibilidade.
   * 
   * @return String compacta formatada.
   */
  public String empacotar() {
    try {
      String nomeSeguro = URLEncoder.encode(this.nome, "UTF-8");
      return nomeSeguro + ";" + this.ip + ";" + this.porta;
    } catch (Exception e) {
      return this.nome + ";" + this.ip + ";" + this.porta;
    }
  }

  /**
   * Desempacota uma string recebida da rede e instancia o objeto InfoUser correspondente.
   * 
   * @param dados String no formato "nome;ip;porta".
   * @return Objeto InfoUser instanciado.
   * @throws IllegalArgumentException Caso o formato dos dados seja invalido.
   */
  public static InfoUser desempacotar(String dados) {
    if (dados == null || dados.isEmpty()) {
      throw new IllegalArgumentException("Dados invalidos na APDU: " + dados);
    }

    String[] partes = dados.split(";");
    if (partes.length < 3) {
      throw new IllegalArgumentException("Formato de usuario invalido na APDU: " + dados);
    }
    try {
      String nomeDecodificado = URLDecoder.decode(partes[0], "UTF-8");
      return new InfoUser(nomeDecodificado, partes[1], Integer.parseInt(partes[2]));
    } catch (Exception e) {
      throw new IllegalArgumentException("Porta invalida na APDU: " + partes[2]);
    }
  }

  /**
   * Retorna uma representacao em string legivel contendo os atributos do usuario.
   * 
   * @return String contendo nome, IP e porta do usuario.
   */
  @Override
  public String toString() {
    return "[Nome: " + nome + " | IP: " + ip + " | Porta: " + porta + "]";
  }

  /**
   * Avalia a igualdade entre dois usuarios baseando-se no Nome de usuario (identidade unica no chat).
   * 
   * @param obj Objeto a ser comparado.
   * @return true se tiverem o mesmo Nome (case-insensitive), false caso contrario.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof InfoUser)) {
      return false;
    }
    InfoUser comparado = (InfoUser) obj;
    if (this.nome == null || comparado.nome == null) {
      return false;
    }
    return this.nome.trim().equalsIgnoreCase(comparado.nome.trim());
  }

  /**
   * Gera o codigo hash do usuario baseado no Nome (case-insensitive).
   * 
   * @return O codigo hash gerado.
   */
  @Override
  public int hashCode() {
    return this.nome != null ? this.nome.trim().toLowerCase().hashCode() : 0;
  }

} // fim class
