/*****************************************************************
* Autor..............: Lucas de Menezes Chaves
* Matricula........: 202310282
* Inicio...........: 21/06/2026
* Ultima alteracao.: 09/09/2026
* Nome.............: RecebedorUDP
* Funcao...........: Ouve o servidor UDP para APDU de CONFIRM,
*                    SEND, SENDVU e SENDPVT
*************************************************************** */

package Network;

import java.net.*;
import java.io.*;
import Protocol.APDU;

public class RecebedorUDP extends Thread{
  private final int minhaPorta;
  private final int portaServidor;
  private final String ipServidor;
  private volatile MensagemListener listener;

  /*********************************************************************
  * Metodo: setListener
  * Funcao: Define o listener para callbacks
  * @param listener objeto MensagemListener
  * @return void
  * ****************************************************************** */
  public void setListener(MensagemListener listener) {
    this.listener = listener;
  }//fim do metodo

  /*********************************************************************
  * Metodo: RecebedorUDP
  * Funcao: Construtor do recebedor UDP
  * @param minhaPorta porta que esta escutando
  * @param ipServidor ip do servidor
  * @param portaServidor porta UDP do servidor
  * @return void
  * ****************************************************************** */
  public RecebedorUDP(int minhaPorta, String ipServidor, int portaServidor) {
    this.minhaPorta = minhaPorta;
    this.ipServidor = ipServidor;
    this.portaServidor = portaServidor;
  }//fim do metodo

  /*********************************************************************
  * Metodo: run
  * Funcao: rodar a thread do recebedorUDP
  * @return void
  * ****************************************************************** */
  public void run() {
    try(DatagramSocket socket = new DatagramSocket(minhaPorta)) {
      byte[] buffer = new byte[8192];

      while(true) {
        DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
        socket.receive(pacote);

        ByteArrayInputStream bais = new ByteArrayInputStream(pacote.getData());
        ObjectInputStream in = new ObjectInputStream(bais);
        APDU apdu = (APDU) in.readObject();

        String operacao = apdu.getOperacao();

        // -------------------------------------------------------
        // SEND / SENDVU - mensagem de grupo recebida
        // -------------------------------------------------------
        if(operacao.equals("SEND") || operacao.equals("SENDVU")) {
          System.out.println("\n[" + apdu.getNomeGrupo() + "] " + apdu.getNomeUsuario() +
            " diz: " + apdu.getTextoMensagem() +
            (apdu.isVisualizacaoUnica() ? " [VU]" : ""));
          System.out.print("\n> ");

          Mensagens.ultimaMensagemId = apdu.getIdMensagem();
          Mensagens.ultimoGrupo = apdu.getNomeGrupo();
          Mensagens.ultimoRemetente = apdu.getNomeUsuario();

          //Envia CONFIRM(2) automatico: mensagem entregue ao cliente
          APDU recebido = new APDU("CONFIRM", apdu.getIdMensagem(), 2, Mensagens.meuNome,
            apdu.getNomeGrupo(), apdu.getNomeUsuario());
          enviarConfirmParaServidor(recebido);

          if(listener != null) {
            listener.onMessageReceived(apdu);
          }//fim do if

        // -------------------------------------------------------
        // SENDPVT - mensagem privada recebida
        // -------------------------------------------------------
        } else if(operacao.equals("SENDPVT")) {
          System.out.println("\n[Privado] " + apdu.getNomeUsuario() +
            " diz: " + apdu.getTextoMensagem());
          System.out.print("\n> ");

          //Envia CONFIRM(2) automatico para o remetente da mensagem privada
          //nomeGrupo contem "@" + nomeRemetente para identificar a conversa privada
          APDU recebido = new APDU("CONFIRM", apdu.getIdMensagem(), 2, Mensagens.meuNome,
            apdu.getNomeGrupo(), apdu.getNomeUsuario());
          enviarConfirmParaServidor(recebido);

          if(listener != null) {
            listener.onPrivateMessageReceived(apdu);
          }//fim do if

        // -------------------------------------------------------
        // CONFIRM - atualizar ticks na UI
        // -------------------------------------------------------
        } else if(operacao.equals("CONFIRM")) {
          String statusTxt = "";
          if(apdu.getStatusRecebido() == 1)       statusTxt = "1 (Recebida pelo Servidor)";
          else if(apdu.getStatusRecebido() == 2)  statusTxt = "2 (Entregue a " + apdu.getNomeUsuario() + ")";
          else if(apdu.getStatusRecebido() == 3)  statusTxt = "3 (Lida por " + apdu.getNomeUsuario() + ")";
          else if(apdu.getStatusRecebido() == -1) statusTxt = "-1 (Destinatario offline ou bloqueou voce)";

          System.out.println("\n[Tick] Mensagem (" + apdu.getIdMensagem().substring(0,4) + "...) Status: " + statusTxt);
          System.out.print("> ");

          if(listener != null) {
            listener.onTickReceived(apdu);
          }//fim do if

        } else {
          System.out.println("[UDP-AVISO] Operacao desconhecida recebida: " + operacao);
        }//fim do if-elseif-else

      }//fim do while
    } catch(Exception e) {
      System.err.println("Erro no RecebedorUDP: " + e.getMessage());
    }//fim do try-catch
  }//fim do metodo

  /*********************************************************************
  * Metodo: enviarConfirmParaServidor
  * Funcao: enviar o CONFIRM para o servidor de maneira automatica
  * @param confirm Apdu de confirmacao
  * @return void
  * ****************************************************************** */
  public void enviarConfirmParaServidor(APDU confirm) {
    try(DatagramSocket socket = new DatagramSocket()) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(baos);
      out.writeObject(confirm);
      out.flush();
      byte[] dados = baos.toByteArray();
      DatagramPacket pacote = new DatagramPacket(dados, dados.length, InetAddress.getByName(ipServidor), portaServidor);
      socket.send(pacote);
    } catch(Exception e) {
      System.err.println("[RecebedorUDP] Erro ao enviar CONFIRM: " + e.getMessage());
    }//fim do try-catch
  }//fim do metodo
}//fim da classe
