/***************************************************************** 
* Autor..............: Lucas de Menezes Chaves
* Matricula........: 202310282
* Inicio...........: 20/06/2026
* Ultima alteracao.: 28/06/2026
* Nome.............: Cliente
* Funcao...........: Cliente maestro, que coordena os outros arquivos do lado do cliente
*************************************************************** */

import java.net.*;
import Protocol.APDU;
import java.io.*;

public class Cliente implements Network.Enviador {
  private String ipServidor;
  private int portaTCP;

  /********************************************************************
  * Metodo: Cliente
  * Funcao: Construtor do Cliente
  * @param ipServidor ip do servidor
  * @param portaTCP porta tcp
  * @return void
  * ****************************************************************** */
 public Cliente(String ipServidor, int portaTCP) {
  this.ipServidor = ipServidor;
  this.portaTCP = portaTCP;
 }//fim do metodo
  /********************************************************************
  * Metodo: enviarComandoTCP
  * Funcao: enviar o comando TCP para se conectar no servidor
  * @param apdu objeto apdu
  * @return String
  * ****************************************************************** */
 public String enviarComandoTCP(APDU apdu) {
  //Tenta conectar no IP e porta fornecidos
  try(Socket socket = new Socket(ipServidor, portaTCP);
    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
    ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
      System.out.println("Conectado ao Servidor! Enviando APDU: " + apdu.getOperacao());
      //Envia a carta
      out.writeObject(apdu);
      out.flush();

      String resposta = (String) in.readObject();
      System.out.println("Resposta do Servidor: " + resposta);
      return resposta;
  } catch(Exception e) {
    System.err.println("Erro na comunicacao com o Servidor: " + e.getMessage());
    return "[JOIN-ERRO] Erro na comunicacao com o Servidor";
  }//fim do try-catch
 }//fim do metodo
  /********************************************************************
  * Metodo: enviarMensagemUDP
  * Funcao: enviar uma mensagem UDP para o servidor
  * @param apdu objeto apdu
  * @param portaServidorUDP porta do servidor UDP
  * @return void
  * ****************************************************************** */
 public void enviarMensagemUDP(APDU apdu, int portaServidorUDP) {
  try(DatagramSocket socket = new DatagramSocket()) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream out = new ObjectOutputStream(baos);
    out.writeObject(apdu);
    out.flush();
    byte[] dados = baos.toByteArray();
    DatagramPacket pacote = new DatagramPacket(dados, dados.length, InetAddress.getByName(ipServidor) , portaServidorUDP);
    socket.send(pacote);
    System.out.println("(Reloginho) Mensagem enviada ao servidor");
  } catch(Exception e) {
    System.err.println("Falha ao enviar a mensagem: "+ e.getMessage());
  }//fim do try-catch
 }//fim do metodo
}//fim da classe
