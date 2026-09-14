/*****************************************************************
* Autor..............: Lucas de Menezes Chaves
* Matricula........: 202310282
* Inicio...........: 23/06/2026
* Ultima alteracao.: 09/09/2026
* Nome.............: MensagemListener
* Funcao...........: Interface para ouvir mensagens recebidas via UDP
*************************************************************** */
package Network;

import Protocol.APDU;

public interface MensagemListener {
  /*********************************************************************
  * Metodo: onMessageReceived
  * Funcao: chamado quando uma mensagem de grupo (SEND ou SENDVU) e recebida
  * @param apdu objeto apdu
  * @return void
  * ****************************************************************** */
  void onMessageReceived(APDU apdu);

  /*********************************************************************
  * Metodo: onPrivateMessageReceived
  * Funcao: chamado quando uma mensagem privada (SENDPVT) e recebida
  * @param apdu objeto apdu
  * @return void
  * ****************************************************************** */
  void onPrivateMessageReceived(APDU apdu);

  /*********************************************************************
  * Metodo: onTickReceived
  * Funcao: chamado quando um tick de confirmacao (CONFIRM) e recebido
  * @param apdu objeto apdu
  * @return void
  * ****************************************************************** */
  void onTickReceived(APDU apdu);
}//fim da interface
