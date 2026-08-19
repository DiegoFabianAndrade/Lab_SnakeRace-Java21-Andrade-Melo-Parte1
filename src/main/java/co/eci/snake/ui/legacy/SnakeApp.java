package co.eci.snake.ui.legacy;

import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Ventana principal y control de interfaz gráfica para SnakeRace.
 *
 * Administra el tablero de juego, el panel de dibujo (GamePanel), los controles de teclado
 * para los jugadores 1 y 2, el botón de acción para Pausar/Reanudar y la barra de estado
 * que muestra de forma consistente las estadísticas del juego al pausar (la serpiente viva
 * más larga y la peor serpiente / primera en morir).
 */
public final class SnakeApp extends JFrame {

  private final Board board;
  private final GamePanel gamePanel;
  private final JButton actionButton;
  private final JLabel statsLabel;
  private final GameClock clock;
  private final java.util.List<Snake> snakes = new java.util.ArrayList<>();

  public SnakeApp() {
    super("The Snake Race — Concurrencia Java 21");
    this.board = new Board(35, 28);

    int N = Integer.getInteger("snakes", 2);
    for (int i = 0; i < N; i++) {
      int x = 2 + (i * 3) % board.width();
      int y = 2 + (i * 2) % board.height();
      var dir = Direction.values()[i % Direction.values().length];
      // Se asigna un identificador único (1-indexed) a cada serpiente
      snakes.add(Snake.of(i + 1, x, y, dir));
    }

    this.gamePanel = new GamePanel(board, () -> snakes);
    this.actionButton = new JButton("Action");
    this.actionButton.setFont(new Font("SansSerif", Font.BOLD, 14));
    this.actionButton.setFocusable(false);

    // Barra de estado para estadísticas en pausa
    this.statsLabel = new JLabel("Estado: En ejecución — Presione ESPACIO o 'Action' para pausar", SwingConstants.CENTER);
    this.statsLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
    this.statsLabel.setBorder(new EmptyBorder(8, 8, 8, 8));
    this.statsLabel.setOpaque(true);
    this.statsLabel.setBackground(new Color(240, 240, 240));

    setLayout(new BorderLayout());
    add(statsLabel, BorderLayout.NORTH);
    add(gamePanel, BorderLayout.CENTER);
    add(actionButton, BorderLayout.SOUTH);

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    pack();
    setLocationRelativeTo(null);

    // Reloj del juego que coordina los repintados en el hilo gráfico de Swing (EDT)
    this.clock = new GameClock(60, () -> SwingUtilities.invokeLater(gamePanel::repaint));

    // Ejecución de cada serpiente de forma autónoma en un Virtual Thread
    var exec = Executors.newVirtualThreadPerTaskExecutor();
    snakes.forEach(s -> exec.submit(new SnakeRunner(s, board, clock)));

    actionButton.addActionListener((ActionEvent e) -> togglePause());

    // Atajo de teclado: Barra espaciadora para Pausar / Reanudar
    gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "pause");
    gamePanel.getActionMap().put("pause", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        togglePause();
      }
    });

    // Controles para Jugador 1 (Flechas)
    if (!snakes.isEmpty()) {
      var player = snakes.get(0);
      InputMap im = gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
      ActionMap am = gamePanel.getActionMap();
      im.put(KeyStroke.getKeyStroke("LEFT"), "left");
      im.put(KeyStroke.getKeyStroke("RIGHT"), "right");
      im.put(KeyStroke.getKeyStroke("UP"), "up");
      im.put(KeyStroke.getKeyStroke("DOWN"), "down");
      am.put("left", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { player.turn(Direction.LEFT); }
      });
      am.put("right", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { player.turn(Direction.RIGHT); }
      });
      am.put("up", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { player.turn(Direction.UP); }
      });
      am.put("down", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { player.turn(Direction.DOWN); }
      });
    }

    // Controles para Jugador 2 (WASD)
    if (snakes.size() > 1) {
      var p2 = snakes.get(1);
      InputMap im = gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
      ActionMap am = gamePanel.getActionMap();
      im.put(KeyStroke.getKeyStroke('A'), "p2-left");
      im.put(KeyStroke.getKeyStroke('D'), "p2-right");
      im.put(KeyStroke.getKeyStroke('W'), "p2-up");
      im.put(KeyStroke.getKeyStroke('S'), "p2-down");
      am.put("p2-left", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.LEFT); }
      });
      am.put("p2-right", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.RIGHT); }
      });
      am.put("p2-up", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.UP); }
      });
      am.put("p2-down", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.DOWN); }
      });
    }

    setVisible(true);
    clock.start();
  }

  /**
   * Alterna entre el estado de Pausa y Reanudación de la partida.
   * Al pausar, suspende tanto el reloj como los Virtual Threads y calcula de forma
   * consistente las estadísticas para mostrarlas en la UI sin tearing.
   */
  private void togglePause() {
    if ("Action".equals(actionButton.getText())) {
      actionButton.setText("Resume");
      clock.pause();

      // Programamos la actualización de estadísticas en el hilo EDT de Swing
      SwingUtilities.invokeLater(this::updatePauseStatistics);
    } else {
      actionButton.setText("Action");
      statsLabel.setText("Estado: En ejecución — Presione ESPACIO o 'Action' para pausar");
      clock.resume();
    }
  }

  /**
   * Calcula y despliega en la barra superior las estadísticas de la partida:
   * 1. La serpiente viva más larga.
   * 2. La peor serpiente (la primera que falleció).
   */
  private void updatePauseStatistics() {
    // 1. Identificar la serpiente viva con mayor longitud
    Snake longestLiving = snakes.stream()
        .filter(Snake::isAlive)
        .max(Comparator.comparingInt(Snake::size))
        .orElse(null);

    // 2. Identificar la primera serpiente fallecida
    Snake firstDead = board.getFirstDeadSnake();

    String longestInfo = (longestLiving != null)
        ? String.format("Serpiente #%d (Longitud: %d)", longestLiving.getId(), longestLiving.size())
        : "Ninguna viva";

    String deadInfo = (firstDead != null)
        ? String.format("Serpiente #%d", firstDead.getId())
        : "Ninguna ha muerto";

    statsLabel.setText(String.format("⏸️ PAUSA | Viva más larga: %s | Peor serpiente (primera en morir): %s", longestInfo, deadInfo));
    gamePanel.repaint();
  }

  public static final class GamePanel extends JPanel {
    private final Board board;
    private final Supplier snakesSupplier;
    private final int cell = 20;

    @FunctionalInterface
    public interface Supplier {
      List<Snake> get();
    }

    public GamePanel(Board board, Supplier snakesSupplier) {
      this.board = board;
      this.snakesSupplier = snakesSupplier;
      setPreferredSize(new Dimension(board.width() * cell + 1, board.height() * cell + 40));
      setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      var g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      // Grilla de fondo
      g2.setColor(new Color(220, 220, 220));
      for (int x = 0; x <= board.width(); x++)
        g2.drawLine(x * cell, 0, x * cell, board.height() * cell);
      for (int y = 0; y <= board.height(); y++)
        g2.drawLine(0, y * cell, board.width() * cell, y * cell);

      // Obstáculos (naranjas con textura)
      g2.setColor(new Color(255, 102, 0));
      for (var p : board.obstacles()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillRect(x + 2, y + 2, cell - 4, cell - 4);
        g2.setColor(Color.RED);
        g2.drawLine(x + 4, y + 4, x + cell - 6, y + 4);
        g2.drawLine(x + 4, y + 8, x + cell - 6, y + 8);
        g2.drawLine(x + 4, y + 12, x + cell - 6, y + 12);
        g2.setColor(new Color(255, 102, 0));
      }

      // Ratones (comida)
      g2.setColor(Color.BLACK);
      for (var p : board.mice()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillOval(x + 4, y + 4, cell - 8, cell - 8);
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 8, y + 8, cell - 16, cell - 16);
        g2.setColor(Color.BLACK);
      }

      // Teleports (flechas rojas)
      Map<Position, Position> tp = board.teleports();
      g2.setColor(Color.RED);
      for (var entry : tp.entrySet()) {
        Position from = entry.getKey();
        int x = from.x() * cell, y = from.y() * cell;
        int[] xs = { x + 4, x + cell - 4, x + cell - 10, x + cell - 10, x + 4 };
        int[] ys = { y + cell / 2, y + cell / 2, y + 4, y + cell - 4, y + cell / 2 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Turbo (rayos de velocidad)
      g2.setColor(new Color(218, 165, 32)); // Color dorado/amarillo
      for (var p : board.turbo()) {
        int x = p.x() * cell, y = p.y() * cell;
        int[] xs = { x + 8, x + 12, x + 10, x + 14, x + 6, x + 10 };
        int[] ys = { y + 2, y + 2, y + 8, y + 8, y + 16, y + 10 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Serpientes
      var currentSnakes = snakesSupplier.get();
      int idx = 0;
      for (Snake s : currentSnakes) {
        // Obtenemos una copia defensiva atómica y segura mediante snapshot()
        var body = s.snapshot().toArray(new Position[0]);
        for (int i = 0; i < body.length; i++) {
          var p = body[i];
          Color base;
          if (!s.isAlive()) {
            base = Color.GRAY; // Serpientes muertas en tono gris
          } else if (idx == 0) {
            base = new Color(0, 170, 0); // Jugador 1: Verde
          } else if (idx == 1) {
            base = new Color(0, 160, 180); // Jugador 2: Cyan/Azul
          } else {
            // Serpientes adicionales en tonos diferenciados
            base = new Color((idx * 67) % 200 + 30, (idx * 113) % 200 + 30, (idx * 179) % 200 + 30);
          }

          int shade = Math.max(0, 40 - i * 4);
          g2.setColor(new Color(
              Math.min(255, base.getRed() + shade),
              Math.min(255, base.getGreen() + shade),
              Math.min(255, base.getBlue() + shade)));
          g2.fillRect(p.x() * cell + 2, p.y() * cell + 2, cell - 4, cell - 4);
        }
        idx++;
      }
      g2.dispose();
    }
  }

  public static void launch() {
    SwingUtilities.invokeLater(SnakeApp::new);
  }
}
