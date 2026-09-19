/*****************************************************************
* Autor..............: Iury Ramos Sodre
* Matricula..........: 202310440
* Inicio.............: 15/06/2026
* Ultima alteracao...: 18/09/2026
* Nome...............: ConexaoException
* Funcao.............: Excecao customizada para falhas de comunicacao nos protocolos de transporte (TCP/UDP).
*************************************************************** */

package exceptions;

/**
 * Excecao lancada quando ocorrem erros na conexao TCP ou UDP no sistema E.D.E.N.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class ConexaoException extends EDENSysException {

  private static final long serialVersionUID = 1L;

  /**
   * Construtor com mensagem de erro explicativa.
   * 
   * @param message A mensagem de erro detalhada
   */
  public ConexaoException(String message) {
    super(message);
  }

  /**
   * Construtor com mensagem e causa raiz encadeada.
   * 
   * @param message A mensagem de erro detalhada
   * @param cause   A excecao original que causou a falha
   */
  public ConexaoException(String message, Throwable cause) {
    super(message, cause);
  }
}
