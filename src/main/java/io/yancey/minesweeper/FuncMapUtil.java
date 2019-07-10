package io.yancey.minesweeper;

import java.util.*;
import java.util.function.*;

public class FuncMapUtil {
	public static class Point implements Comparable<Point> {
		public final int x;
		public final int y;
		
		public Point(int x, int y) {
			this.x = x;
			this.y = y;
		}
		
		@Override
		public String toString() {
			return String.format("(%d, %d)", x, y);
		}
		
		@Override
		public int hashCode() {
			return 13 + x * 31 + y;
		}
		
		@Override
		public boolean equals(Object o) {
			if(o == null || ! (o instanceof Point)) return false;
			Point p = (Point)o;
			return p.x == x && p.y == y;
		}

		@Override
		public int compareTo(Point p) {
			return p.x == x? p.y - y: p.x - x;
		}
	}
	
	public static class InferencePoint extends Point {
		public final boolean isMine;
		
		public InferencePoint(int x, int y, boolean isMine) {
			super(x, y);
			this.isMine = isMine;
		}
		
		public InferencePoint(Point p, boolean isMine) {
			super(p.x, p.y);
			this.isMine = isMine;
		}
		
		@Override
		public String toString() {
			return String.format("(%d, %d, %b)", x, y, isMine);
		}
		
		@Override
		public int hashCode() {
			return 13 + x * 31 + y + (isMine? 0: 65536);
		}
		
		@Override
		public boolean equals(Object o) {
			if(o == null || ! (o instanceof InferencePoint)) return false;
			InferencePoint p = (InferencePoint)o;
			return p.x == x && p.y == y && p.isMine == isMine;
		}
	}
	
	public static class InferenceGroup {
		public Set<Point> revealedPoints = new HashSet<>();
		public Set<Point> unrevealedPoints = new HashSet<>();
		public String toString() {
			return "{" + unrevealedPoints + ", " + revealedPoints + "}";
		}
	}
	
	public static Point w(int x, int y) {
		return new Point(x, y);
	}
	
	public static interface BiIntToObj<T> {
		T apply(int x, int y);
	}
	
	public static interface BiIntToBool {
		boolean apply(int x, int y);
	}
	
	public static interface BiIntToVoid {
		void apply(int x, int y);
	}
	
	public static <T> Function<Point, T> f(BiIntToObj<T> f) {
		return xy -> f.apply(xy.x, xy.y);
	}
	
	public static <T> Predicate<Point> p(BiIntToBool f) {
		return xy -> f.apply(xy.x, xy.y);
	}
	
	public static <T> Consumer<Point> c(BiIntToVoid f) {
		return xy -> f.apply(xy.x, xy.y);
	}
}
