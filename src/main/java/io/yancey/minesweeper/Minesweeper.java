package io.yancey.minesweeper;

import static io.yancey.minesweeper.FuncMapUtil.*;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.Queue;
import java.util.stream.*;

import javax.swing.*;

import io.yancey.minesweeper.FuncMapUtil.Point;

public class Minesweeper extends JPanel {
	private static final int MINE_SIZE = 20;
	private static final Point[] OFFSETS = {
			w(-1, -1),
			w( 0, -1),
			w( 1, -1),
			w(-1,  0),
			//w( 0,  0),
			w( 1,  0),
			w(-1,  1),
			w( 0,  1),
			w( 1,  1),
	};
	
	private static final Color[] COLOR_MAP = {
			new Color(0f, 0, 0),
			new Color(.33f, 0, 0),
			new Color(.67f, 0, 0),
			new Color(1f, 0, 0),
			new Color(0, 0, 1f),
			new Color(0, 1f, 0),
			new Color(.5f, 0, .5f),
			new Color(.8f, .8f, 0),
	};
	
	private Random r = new Random();
	
	private int width;
	private int height;
	
	private float mineDensity;
	
	private boolean[][] mines;
	private boolean[][] revealed;
	private boolean[][] flagged;
	private int[][] counts;
	
	private boolean newGame;
	
	public Minesweeper(int width, int height, float mineDensity) {
		this.width = width;
		this.height = height;
		this.mineDensity = mineDensity;
		
		this.setPreferredSize(new Dimension(width*MINE_SIZE, height*MINE_SIZE));
		
		clearBoard();
		
		this.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int x = e.getX() / MINE_SIZE;
				int y = e.getY() / MINE_SIZE;
				
				if(newGame) {
					generateMines(x, y);
					newGame = false;
				}
				
				if ((e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0) {
					autoPlay();
				}
				else if((e.getModifiersEx() != 0 || e.getButton() != MouseEvent.BUTTON1) && !revealed[x][y]) {
					flagged[x][y] = !flagged[x][y];
					repaint();
				} else if(e.getClickCount() > 1 && revealed[x][y]) {
					if(flagCount(x, y) == counts[x][y]) {
						neighbors(x, y)
							.filter(p((nx, ny) -> !flagged[nx][ny]))
							.forEach(c((nx, ny) -> reveal(nx, ny)));
					}
				} else if(!flagged[x][y] && !revealed[x][y]) {
					reveal(x, y);
				}
				checkWin();
			}
		});
	}
	
	private void autoPlay() {
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				if (revealed[x][y]) {
					if (flagCount(x, y) == counts[x][y]) {
						neighbors(x, y)
							.filter(p((nx, ny) -> !flagged[nx][ny]))
							.forEach(c((nx, ny) -> reveal(nx, ny)));
					}
					if (maybeMineCount(x, y) == counts[x][y]) {
						neighbors(x,y)
							.filter(p((nx, ny) -> !revealed[nx][ny]))
							.forEach(c((nx,ny) -> flagged[nx][ny] = true));
					}
				}
			}
		}
		repaint();
		System.out.println("done!");
	}
	
	private void generateMines(int mx, int my) {
		do {
			mines = new boolean[width][height];
			counts = new int[width][height];
			for(int i = 0; i < width*height*mineDensity; i++) {
				int x = r.nextInt(width), y = r.nextInt(height);
				if (mines[x][y]) {
					//i--;
					continue;
				}
				mines[x][y] = true;
				neighbors(x,y).forEach(c((nx,ny) -> counts[nx][ny]++));
			}
			System.out.print(".");
		} while(counts[mx][my] != 0 && !mines[mx][my]);
	}

	private void checkWin() {
		boolean alreadyLost = false;
		for(int x = 0; x < width; x++) {
			for(int y = 0; y < height; y++) {
				if(!flagged[x][y] && !revealed[x][y]) return;
				if(flagged[x][y] && !mines[x][y]) return;
				if(revealed[x][y] && mines[x][y]) alreadyLost = true;
			}
		}
		if(alreadyLost) {
			JOptionPane.showMessageDialog(this, "You lost, but you got all the mines.");
		} else {
			JOptionPane.showMessageDialog(this, "You win!");
		}
		clearBoard();
		repaint();
	}

	private void reveal(int x, int y) {
		if(counts[x][y] == 0) {
			revealAll(x, y);
			repaint();
			return;
		} else {
			revealed[x][y] = true;
		}
		
		repaint();
		
		if(mines[x][y]) {
			if(JOptionPane.showOptionDialog(this, "You lost!", "You lost",
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE,
					null, new String[]{"New Game", "Continue Playing"}, "New Game") == JOptionPane.OK_OPTION) {
				clearBoard();
				repaint();
			} else {
				// flag revealed mines, to enable double-click
				flagged[x][y] = true;
			}
		}
	}
	
	private void clearBoard() {
		revealed = new boolean[width][height];
		flagged = new boolean[width][height];
		newGame = true;
	}

	private void revealAll(int x, int y) {
		Queue<Point> points = new LinkedList<>();
		points.add(w(x, y));
		while(!points.isEmpty()) {
			Point p = points.remove();
			if(!revealed[p.x][p.y]) {
				neighbors(p.x, p.y)
					.filter(p((nx, ny) -> counts[nx][ny] > 0))
					.forEach(c((nx, ny) -> revealed[nx][ny] = true));
				neighbors(p.x, p.y)
					.filter(p((nx, ny) -> counts[nx][ny] == 0 && !revealed[nx][ny]))
					.forEach(p2 -> points.add(p2));
				revealed[p.x][p.y] = true;
			}
		}
	}
	
	private int maybeMineCount(int x, int y) {
		return neighbors(x, y)
				.map(f((nx, ny) -> !revealed[nx][ny] || mines[nx][ny]))
				.collect(Collectors.summingInt(isOpen -> isOpen? 1: 0));
	}
	
	private int flagCount(int x, int y) {
		return neighbors(x, y)
				.map(f((nx, ny) -> flagged[nx][ny]))
				.collect(Collectors.summingInt(isFlagged -> isFlagged? 1: 0));
	}
	
	private Stream<Point> neighbors(int x, int y) {
		return Arrays.stream(OFFSETS)
				.map(f((nx, ny) -> w(x + nx, y + ny)))
				.filter(p((nx, ny) -> nx >= 0 && ny >= 0 && nx < width && ny < height));
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width*MINE_SIZE, height*MINE_SIZE);
		
		g.setColor(Color.BLACK);
		for(int x = 1; x < width; x++) {
			g.drawLine(x*MINE_SIZE, 0, x*MINE_SIZE, height*MINE_SIZE);
		}
		for(int y = 1; y < height; y++) {
			g.drawLine(0, y*MINE_SIZE, width*MINE_SIZE, y*MINE_SIZE);
		}
		
		for(int x = 0; x < width; x++) {
			for(int y = 0; y < height; y++) {
				int gx = x*MINE_SIZE, gy = y*MINE_SIZE;
				if(revealed[x][y]) {
					if(mines[x][y]) {
						g.setColor(Color.RED);
					} else {
						g.setColor(Color.LIGHT_GRAY);
					}
					g.fillRect(gx, gy, MINE_SIZE, MINE_SIZE);
					if(counts[x][y] > 0) {
						g.setColor(COLOR_MAP[counts[x][y]-1]);
						FontMetrics fm = g.getFontMetrics();
						String countStr = Integer.toString(counts[x][y]);
						g.drawString(countStr, 
								gx + MINE_SIZE/2 - fm.stringWidth(countStr)/2, 
								gy + MINE_SIZE/2 + fm.getAscent()/2);
					}
				} else if(flagged[x][y]) {
					g.setColor(Color.BLACK);
					g.drawLine(gx, gy, gx + MINE_SIZE, gy + MINE_SIZE);
					g.drawLine(gx, gy + MINE_SIZE, gx + MINE_SIZE, gy);
				}
			}
		}
	}
	
	public static void main(String[] args) {
		JFrame frame = new JFrame("Minesweeper");
		Minesweeper sweeper = new Minesweeper(40, 40, 0.2f);
		frame.setContentPane(sweeper);
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setResizable(false);
		frame.setVisible(true);
	}
}
