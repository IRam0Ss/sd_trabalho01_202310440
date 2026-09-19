/*****************************************************************
* Autor..............: Iury Ramos Sodre
* Matricula..........: 202310440
* Inicio.............: 15/06/2026
* Ultima alteracao...: 18/09/2026
* Nome...............: TutorialOverlay
* Funcao.............: Componente visual responsavel por apresentar o tutorial interativo (Coach Marks) na GUI.
*************************************************************** */

package view;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.List;

/**
 * Camada de visualizacao para exibicao de tutorial guiado interativo (Coach Marks).
 * Renderiza uma mascara escura com recorte vazado no componente em foco e baloes explicativos.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class TutorialOverlay extends Pane {

  /**
   * Representa uma etapa especifica do tutorial interativo.
   */
  public static class TutorialStep {
    Node targetNode;
    String title;
    String description;

    /**
     * Construtor de uma etapa individual do tutorial.
     * 
     * @param targetNode  No JavaFX alvo do destaque.
     * @param title       Titulo explicativo da etapa.
     * @param description Descricao detalhada da funcionalidade.
     */
    public TutorialStep(Node targetNode, String title, String description) {
      this.targetNode = targetNode;
      this.title = title;
      this.description = description;
    }
  }

  private StackPane root;
  private List<TutorialStep> steps;
  private int currentStepIndex = 0;

  private Path overlayPath;
  private VBox balloon;

  /**
   * Construtor do painel de sobreposicao do tutorial interativo.
   * 
   * @param root  Painel raiz da interface sobre o qual a mascara sera renderizada.
   * @param steps Lista sequencial de etapas do tutorial.
   */
  public TutorialOverlay(StackPane root, List<TutorialStep> steps) {
    this.root = root;
    this.steps = steps;

    // Intercepta todos os cliques para bloquear interacoes com a UI abaixo
    this.setOnMouseClicked(e -> e.consume());
    this.setOnMousePressed(e -> e.consume());

    overlayPath = new Path();
    // Cor de fundo padrao do E.D.E.N. (Verde militar escuro com opacidade 75%)
    overlayPath.setFill(Color.rgb(10, 15, 5, 0.75));
    overlayPath.setFillRule(FillRule.EVEN_ODD);
    overlayPath.setStroke(Color.TRANSPARENT);

    balloon = new VBox(12);
    balloon.setStyle(
        "-fx-background-color: #232d0f; "
            + "-fx-border-color: #8a9b3a; -fx-border-width: 2px; "
            + "-fx-background-radius: 14px; -fx-border-radius: 14px; "
            + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.6), 18, 0, 0, 8);");
    balloon.setPadding(new Insets(18, 22, 18, 22));
    balloon.setMaxWidth(380);

    getChildren().addAll(overlayPath, balloon);

    // Atualiza a mascara se a tela for redimensionada
    root.widthProperty().addListener((obs, oldVal, newVal) -> updateOverlay());
    root.heightProperty().addListener((obs, oldVal, newVal) -> updateOverlay());
  }

  /**
   * Inicia a apresentacao sequencial do tutorial a partir da primeira etapa.
   */
  public void start() {
    if (steps == null || steps.isEmpty())
      return;
    currentStepIndex = 0;
    root.getChildren().add(this);
    updateOverlay();
  }

  /**
   * Finaliza o tutorial e remove o overlay da arvore de componentes visuais.
   */
  private void end() {
    root.getChildren().remove(this);
  }

  /**
   * Recalcula a geometria de recorte da mascara e reposiciona o balao descritivo.
   */
  private void updateOverlay() {
    if (currentStepIndex >= steps.size()) {
      end();
      return;
    }

    TutorialStep step = steps.get(currentStepIndex);
    Node target = step.targetNode;

    // Se o node alvo nao estiver visivel na cena ainda, ignora as contas
    if (target.getScene() == null)
      return;

    // Obtem as coordenadas do alvo relativo a tela (cena)
    Bounds bounds = target.localToScene(target.getBoundsInLocal());

    double padding = 6;
    double x = bounds.getMinX() - padding;
    double y = bounds.getMinY() - padding;
    double w = bounds.getWidth() + padding * 2;
    double h = bounds.getHeight() + padding * 2;

    overlayPath.getElements().clear();

    double screenW = root.getWidth();
    double screenH = root.getHeight();

    // Desenha a mascara sobre a tela inteira
    overlayPath.getElements().add(new MoveTo(0, 0));
    overlayPath.getElements().add(new LineTo(screenW, 0));
    overlayPath.getElements().add(new LineTo(screenW, screenH));
    overlayPath.getElements().add(new LineTo(0, screenH));
    overlayPath.getElements().add(new LineTo(0, 0));
    overlayPath.getElements().add(new ClosePath());

    // Desenha o 'buraco' onde o componente fica visivel (sentido oposto)
    overlayPath.getElements().add(new MoveTo(x, y));
    overlayPath.getElements().add(new LineTo(x, y + h));
    overlayPath.getElements().add(new LineTo(x + w, y + h));
    overlayPath.getElements().add(new LineTo(x + w, y));
    overlayPath.getElements().add(new LineTo(x, y));
    overlayPath.getElements().add(new ClosePath());

    // Atualiza o texto do balao
    balloon.getChildren().clear();

    Label lblStepCounter = new Label("DIRETRIZ " + (currentStepIndex + 1) + " / " + steps.size());
    lblStepCounter.setFont(Font.font("Consolas", FontWeight.BOLD, 10));
    lblStepCounter.setTextFill(Color.web("#8a9b3a"));

    Label lblTitle = new Label(step.title);
    lblTitle.setFont(Font.font("Impact", FontWeight.NORMAL, 20));
    lblTitle.setTextFill(Color.web("#c9d873"));
    lblTitle.setWrapText(true);

    Text txtDesc = new Text(step.description);
    txtDesc.setFont(Font.font("Consolas", 13));
    txtDesc.setFill(Color.web("#e5e8d7"));
    txtDesc.setWrappingWidth(340);

    HBox buttonBox = new HBox(10);
    buttonBox.setAlignment(Pos.CENTER_RIGHT);

    Button btnSkip = new Button("Pular Tutorial");
    btnSkip.setStyle(
        "-fx-background-color: transparent; -fx-text-fill: #8a9b3a; -fx-cursor: hand; -fx-font-family: 'Consolas'; -fx-font-size: 12px; -fx-font-weight: bold;");
    btnSkip.setOnAction(e -> end());

    Button btnNext = new Button(currentStepIndex == steps.size() - 1 ? "\u2713 Concluir" : "Proximo \u2192");
    btnNext.setStyle(
        "-fx-background-color: linear-gradient(to bottom, #8a9b3a, #5b6623); -fx-text-fill: #e5e8d7; -fx-padding: 7px 16px; -fx-background-radius: 20px; -fx-border-color: #3f4a23; -fx-border-radius: 20px; -fx-border-width: 1px; -fx-cursor: hand; -fx-font-family: 'Consolas'; -fx-font-weight: bold; -fx-font-size: 12px;");
    btnNext.setOnAction(e -> {
      currentStepIndex++;
      updateOverlay();
    });

    buttonBox.getChildren().addAll(btnSkip, btnNext);
    balloon.getChildren().addAll(lblStepCounter, lblTitle, txtDesc, buttonBox);

    // Animacao suave de fade ao trocar de etapa
    balloon.setOpacity(0.0);
    javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(180), balloon);
    ft.setFromValue(0.0);
    ft.setToValue(1.0);
    ft.play();

    // Posiciona o balao na tela perto do buraco
    balloon.applyCss();
    balloon.layout();

    double balloonW = balloon.prefWidth(-1);
    if (balloonW == 0)
      balloonW = 350;
    double balloonH = balloon.prefHeight(balloonW);

    // Tenta posicionar a direita
    double bX = x + w + 15;
    double bY = y;

    if (bX + balloonW > screenW) {
      // Tenta a esquerda
      bX = x - balloonW - 15;
    }

    if (bX < 0) {
      // Embaixo
      bX = x + (w / 2) - (balloonW / 2);
      bY = y + h + 15;
    }

    if (bY + balloonH > screenH) {
      bY = screenH - balloonH - 15;
    }
    if (bY < 0) {
      bY = 15;
    }
    if (bX < 0) {
      bX = 15;
    }
    if (bX + balloonW > screenW) {
      bX = screenW - balloonW - 15;
    }

    balloon.setLayoutX(bX);
    balloon.setLayoutY(bY);
  }
}
