/***************************************************************** 
* Autor..............: Lucas de Menezes Chaves
* Matricula........: 202310282
* Inicio...........: 23/06/2026
* Ultima alteracao.: 23/06/2026
* Nome.............: Enviador
* Funcao...........: Interface para envio de mensagens
*************************************************************** */
package Network;

import Protocol.APDU;

public interface Enviador {
  /********************************************************************
  * Metodo: enviarComandoTCP
  * Funcao: enviar o comando TCP
  * @param apdu objeto apdu
  * @return String resposta do servidor
  * ****************************************************************** */
  String enviarComandoTCP(APDU apdu);

  /********************************************************************
  * Metodo: enviarMensagemUDP
  * Funcao: enviar mensagem UDP
  * @param apdu objeto apdu
  * @param portaServidorUDP porta do servidor
  * @return void
  * ****************************************************************** */
  void enviarMensagemUDP(APDU apdu, int portaServidorUDP);
}//fim da interface
