package io.yancey.minesweeper;

import java.util.function.*;

public class FuncMapUtil {
	public static class Point {
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
