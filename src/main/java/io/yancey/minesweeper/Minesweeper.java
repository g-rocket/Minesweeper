package io.yancey.minesweeper;

import static io.yancey.minesweeper.FuncMapUtil.*;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.stream.*;

import javax.swing.*;

import io.yancey.minesweeper.FuncMapUtil.*;
import io.yancey.minesweeper.FuncMapUtil.Point;

public class Minesweeper extends JPanel {
	private static final ExecutorService threadpool = Executors.newSingleThreadExecutor();
	
	private static final int MINE_SIZE = 20;
	private static final Point[] OFFSETS = {
			w(-1, -1),
			w( 0, -1),
			w( 1, -1),
			w( 1,  0),
			w( 1,  1),
			w( 0,  1),
			w(-1,  1),
			w(-1,  0),
			//w( 0,  0),
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
				receiveMouseClick(e);
			}
		});
	}
	
	private void receiveMouseClick(MouseEvent e) {
		System.out.println("click!");
		int x = e.getX() / MINE_SIZE;
		int y = e.getY() / MINE_SIZE;
		
		if(newGame) {
			generateMines(x, y);
			newGame = false;
		}
		
		if ((e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0) {
			threadpool.execute(() -> {
				if (e.getButton() == MouseEvent.BUTTON2) {
					while(true) {
						autoPlay(true);
						InferencePoint p = inferOne();
						if (p == null) break;
						try {
							Thread.sleep(200);
						} catch (InterruptedException e1) {
							throw new RuntimeException(e1);
						}
						if (p.isMine) {
							flagged[p.x][p.y] = true; 
						} else {
							revealed[p.x][p.y] = true; 
						}
						repaint();
						checkWin();
					}
				}
				else if (e.getButton() != MouseEvent.BUTTON1) {
					InferencePoint p = inferOne();
					if (p != null) {
						if (p.isMine) {
							flagged[p.x][p.y] = true; 
						} else {
							revealed[p.x][p.y] = true; 
						}
					}
				} else {
					autoPlay(true);
				}
				repaint();
				checkWin();
				System.out.println("done!");
			});
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
	
	private void autoPlay(boolean render) {
		System.out.println("autoplay!");
		boolean done = false;
		int maxiters = width*height+1;
		int i = 0;
		while(!done && i++ < maxiters) {
			done = true;
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					if (revealed[x][y]) {
						int flagCount = flagCount(x, y);
						int maybeMineCount = maybeMineCount(x, y);
						if (flagCount == maybeMineCount) continue;
						if (flagCount == counts[x][y]) {
							done = false;
							neighbors(x, y)
								.filter(p((nx, ny) -> !flagged[nx][ny]))
								.forEach(c((nx, ny) -> revealed[nx][ny] = true));
						}
						if (maybeMineCount == counts[x][y]) {
							done = false;
							neighbors(x,y)
								.filter(p((nx, ny) -> !revealed[nx][ny]))
								.forEach(c((nx,ny) -> flagged[nx][ny] = true));
						}
					}
				}
			}
			if (render) {
				repaint();
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}
		if (i == maxiters) {
			System.err.println("autoplay did an infinite loop");
		}
	}
	
	private List<InferenceGroup> findInferenceGroups() {
		Set<Point> processedPoints = new HashSet<>();
		List<InferenceGroup> groups = new ArrayList<>();
		
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				if (!processedPoints.contains(w(x,y))) {
					InferenceGroup g = findInferenceGroup(x, y);
					processedPoints.addAll(g.revealedPoints);
					processedPoints.addAll(g.unrevealedPoints);
					
					// <= 1 already solved by basic inference
					if (g.unrevealedPoints.size() > 1) {
						groups.add(g);
					}
				}
			}
		}
		
		return groups;
	}
	
	private InferenceGroup findInferenceGroup(int sx, int sy) {
		if (flagged[sx][sy]) return new InferenceGroup();
		
		InferenceGroup g = new InferenceGroup();
		Queue<Point> toProcess = new LinkedList<>();
		toProcess.add(w(sx, sy));
		if (revealed[sx][sy]) {
			g.revealedPoints.add(w(sx,sy));
		} else {
			g.unrevealedPoints.add(w(sx,sy));
		}
		
		while (!toProcess.isEmpty()) {
			Point next = toProcess.remove();
			
			if (revealed[next.x][next.y]) {
				neighbors(next.x, next.y)
					.filter(p((x,y) -> !revealed[x][y] && !flagged[x][y])
						.and(p -> !g.unrevealedPoints.contains(p)))
					.forEach(p -> {
						toProcess.add(p);
						g.unrevealedPoints.add(p);
					});
			} else {
				neighbors(next.x, next.y)
				.filter(p((x,y) -> revealed[x][y] && !flagged[x][y])
					.and(p -> !g.revealedPoints.contains(p)))
				.forEach(p -> {
					toProcess.add(p);
					g.revealedPoints.add(p);
				});
			}
		}
		
		return g;
	}
	
	private InferencePoint inferOne(InferenceGroup g) {
		for (Point p: g.unrevealedPoints) {
			boolean canBeMine = isSatisfyable(g, new InferencePoint(p, true));
			boolean canBeOpen = isSatisfyable(g, new InferencePoint(p, false));
			if (canBeMine ^ canBeOpen) {
				return new InferencePoint(p, canBeMine);
			}
			if (!canBeMine && !canBeOpen) {
				System.err.printf("Inference failed for %s\n",p);
			}
		}
		
		return null;
	}
	
	private InferencePoint inferOne() {
		System.out.println("infer!");
		int i = 0;
		for (InferenceGroup g: findInferenceGroups()) {
			InferencePoint p = inferOne(g);
			if (p != null) return p;
		}
		
		return null;
	}
	
	private boolean isSatisfyable(InferenceGroup g, InferencePoint guess) {
		Point[] points = Stream.concat(Stream.of(guess), g.unrevealedPoints.stream().filter(p -> !p.equals(guess)).sorted()).toArray(Point[]::new);
		boolean[] guesses = new boolean[points.length];
		
		int guessIndex = 1;
		guesses[0] = guess.isMine;
		
		while (guessIndex > 0 && guessIndex < points.length) {
			if (isValidGuess(g.revealedPoints, points, guesses, guessIndex + 1)) {
				guessIndex++;
			} else {
				while (guesses[guessIndex] && guessIndex > 0) {
					guesses[guessIndex] = false;
					guessIndex--;
				}
				guesses[guessIndex] = true;
			}
		}

		return guessIndex == points.length;
	}
	
	private boolean isValidGuess(Set<Point> toCheck, Point[] points, boolean[] guesses, int validGuesses) {
		return toCheck.stream().allMatch(p((x, y) -> {
			int modifiedFlagCount = 0;
			int modifiedOpenCount = 0;
			
			for (Point n: (Iterable<Point>)neighbors(x, y)::iterator) {
				boolean shouldCount = !revealed[n.x][n.y];
				boolean countAsFlag = flagged[n.x][n.y];
				
				if (validGuesses > 0 && n.equals(points[0])) {
					shouldCount = guesses[0];
					countAsFlag = guesses[0];
				}
				int i = Arrays.binarySearch(points, 1, validGuesses, n);
				if (i > 0) {
					shouldCount = guesses[i];
					countAsFlag = guesses[i];
				}
				
				if (shouldCount) {
					if (countAsFlag) {
						modifiedFlagCount++;
					} else {
						modifiedOpenCount++;
					}
				}
			};
			
			return modifiedFlagCount <= counts[x][y] && modifiedOpenCount >= (counts[x][y] - modifiedFlagCount);
		}));
	}
	
	private void generateMines(int mx, int my) {
		System.out.println("newgame");
		int difficulty = -1;
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
			
			if (mines[mx][my] || counts[mx][my] > 0) continue;

			clearBoard();
			revealed[mx][my] = true;
			for (difficulty = 1; !gameOver(); difficulty++) {
				autoPlay(false);
				if (gameOver()) break;
				InferencePoint p = inferOne();
				if (p == null) {
					difficulty = -difficulty;
					System.out.println("Can't infer");
					break;
				}
				if (p.isMine) {
					flagged[p.x][p.y] = true; 
				} else {
					revealed[p.x][p.y] = true; 
				}
			}
			clearBoard();
			System.out.println(difficulty);
		} while (difficulty < 0);
	}

	private boolean gameOver() {
		for(int x = 0; x < width; x++) {
			for(int y = 0; y < height; y++) {
				if(!flagged[x][y] && !revealed[x][y]) return false;
				//if(flagged[x][y] && !mines[x][y]) return false;
			}
		}
		return true;
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
		
		// color inference groups
		Random r = new Random(12358);
		for (InferenceGroup ig: findInferenceGroups()) {
			g.setColor(new Color(r.nextInt(256), r.nextInt(256), r.nextInt(256)));
			ig.revealedPoints.forEach(c((x, y) -> drawTriangle(x, y, g)));
			ig.unrevealedPoints.forEach(c((x, y) -> drawTriangle(x, y, g)));
		}
	}
	
	private static void drawTriangle(int x, int y, Graphics g) {
		int cx = x*MINE_SIZE, cy = y*MINE_SIZE;
		g.fillPolygon(new int[]{cx, cx, cx+5}, new int[]{cy, cy+5, cy}, 3);
	}
	
	public static void main(String[] args) {
		JFrame frame = new JFrame("Minesweeper");
		Minesweeper sweeper = new Minesweeper(40, 40, 0.2f);
		frame.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				sweeper.receiveMouseClick(e);
			}
		});
		frame.setContentPane(sweeper);
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setResizable(false);
		frame.setVisible(true);
	}
}
