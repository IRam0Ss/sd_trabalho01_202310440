/**
 * Autor: Iury Ramos Sodre
 * Matricula: 202310440
 * Inicio: 15/06/2026
 * Ultima alteracao: 14/09/2026
 * Nome: EDENSysException
 * Funcao: Excecao raiz da hierarquia de excecoes do sistema E.D.E.N.
 */

package exceptions;

/**
 * Excecao base abstrata para todos os erros e condicoes excepcionais do sistema E.D.E.N.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class EDENSysException extends Exception {

  private static final long serialVersionUID = 1L;

  /**
   * Construtor com mensagem descritiva do erro.
   * 
   * @param message A mensagem de erro
   */
  public EDENSysException(String message) {
    super(message);
  }

  /**
   * Construtor com mensagem de erro e causa original.
   * 
   * @param message A mensagem de erro
   * @param cause   A excecao original causadora
   */
  public EDENSysException(String message, Throwable cause) {
    super(message, cause);
  }
}
