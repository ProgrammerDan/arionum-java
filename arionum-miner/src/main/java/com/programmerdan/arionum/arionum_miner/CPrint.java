package com.programmerdan.arionum.arionum_miner;

import com.diogonunes.jcdp.color.ColoredPrinter;
import com.diogonunes.jcdp.color.api.Ansi.Attribute;
import com.diogonunes.jcdp.color.api.Ansi.BColor;
import com.diogonunes.jcdp.color.api.Ansi.FColor;

public class CPrint {
	private ColoredPrinter coPrint = new ColoredPrinter.Builder(2, false).build();
	private boolean color = false;

	private Attribute attr = Attribute.NONE;
	private FColor fore = FColor.WHITE;
	private BColor back = BColor.BLACK;
	
	public CPrint(boolean color) {
		this.color = color;
	}
	
	public CPrint p(Object msg) {
		if (color) {
			coPrint.print(msg, attr, fore, back);
		} else {
			System.out.print(msg);
		}
		return this;
	}
	
	public CPrint fp(String format, Object msg) {
		if (color) {
			coPrint.print(String.format(format, msg), attr, fore, back);
		} else {
			System.out.print(String.format(format, msg));
		}
		return this;
	}
	
	public CPrint fs(Object msg) {
		if (color) {
			coPrint.print(String.format("%s", msg), attr, fore, back);
		} else {
			System.out.print(String.format("%s", msg));
		}
		return this;		
	}
	
	public CPrint fd(Object msg) {
		if (color) {
			coPrint.print(String.format("%d", msg), attr, fore, back);
		} else {
			System.out.print(String.format("%d", msg));
		}
		return this;		
	}
	
	public CPrint ff(Object msg) {
		if (color) {
			coPrint.print(String.format("%f", msg), attr, fore, back);
		} else {
			System.out.print(String.format("%f", msg));
		}
		return this;		
	}
	
	public CPrint ln(Object msg) {
		if (color) { 
			coPrint.println(msg, attr, fore, back);
		} else {
			System.out.println(msg);
		}
		return this;
	}
	
	public CPrint ln() {
		if (color) {
			coPrint.println("", attr, fore, back);
		} else {
			System.out.println();
		}
		return this;
	}
	
	public CPrint a(Attribute attr) {
		if (color) coPrint.setAttribute(attr);
		this.attr = attr;
		return this;
	}
	
	public CPrint f(FColor color) {
		if (this.color) coPrint.setForegroundColor(color);
		this.fore = color;
		return this;
	}
	
	public CPrint b(BColor color) {
		if (this.color) coPrint.setBackgroundColor(color);
		this.back = color;
		return this;
	}
	
	public CPrint clr() {
		if (color) coPrint.clear();
		this.attr = Attribute.NONE;
		this.fore = FColor.WHITE;
		this.back = BColor.BLACK;
		return this;
	}
	
	public CPrint updateLabel() {
		return this.clr().a(Attribute.BOLD).f(FColor.CYAN).b(BColor.BLACK);
	}

	public CPrint updateMsg() {
		return this.clr().a(Attribute.NONE).f(FColor.CYAN).b(BColor.BLACK);
	}

	public CPrint info() {
		return this.clr().a(Attribute.DARK).f(FColor.CYAN).b(BColor.BLACK);
	}
	
	public CPrint statusLabel() {
		return this.clr().a(Attribute.NONE).f(FColor.CYAN).b(BColor.BLACK);
	}
	
	public CPrint normData() {
		 return this.clr().a(Attribute.BOLD).f(FColor.GREEN).b(BColor.BLACK);
	}

	public CPrint dlData() {
		return this.clr().a(Attribute.BOLD).f(FColor.YELLOW).b(BColor.BLACK);
	}
	
	public CPrint unitLabel() {
		return this.clr().a(Attribute.NONE).f(FColor.WHITE).b(BColor.BLACK);
	}
	
	public CPrint textData() {
		return this.clr().a(Attribute.DARK).f(FColor.WHITE).b(BColor.BLACK);
	}
	
	public CPrint headers() {
		return this.clr().f(FColor.CYAN).a(Attribute.LIGHT).b(BColor.BLACK);
	}
}
