package com.programmerdan.arionum.arionum_miner;

import com.diogonunes.jcdp.color.ColoredPrinter;
import com.diogonunes.jcdp.color.api.Ansi.Attribute;
import com.diogonunes.jcdp.color.api.Ansi.BColor;
import com.diogonunes.jcdp.color.api.Ansi.FColor;

public class CPrint {
	private ColoredPrinter coPrint = new ColoredPrinter.Builder(2, false).build();
	private boolean color = false;
	
	public CPrint(boolean color) {
		this.color = color;
	}
	
	public CPrint p(Object msg) {
		if (color) {
			coPrint.print(msg);
		} else {
			System.out.print(msg);
		}
		return this;
	}
	
	public CPrint fp(String format, Object msg) {
		if (color) {
			coPrint.print(String.format(format, msg));
		} else {
			System.out.print(String.format(format, msg));
		}
		return this;
	}
	
	public CPrint fs(Object msg) {
		if (color) {
			coPrint.print(String.format("%s", msg));
		} else {
			System.out.print(String.format("%s", msg));
		}
		return this;		
	}
	
	public CPrint fd(Object msg) {
		if (color) {
			coPrint.print(String.format("%d", msg));
		} else {
			System.out.print(String.format("%d", msg));
		}
		return this;		
	}
	
	public CPrint ff(Object msg) {
		if (color) {
			coPrint.print(String.format("%f", msg));
		} else {
			System.out.print(String.format("%f", msg));
		}
		return this;		
	}
	
	public CPrint ln(Object msg) {
		if (color) { 
			coPrint.println(msg);
		} else {
			System.out.println(msg);
		}
		return this;
	}
	
	public CPrint ln() {
		if (color) {
			coPrint.println("");
		} else {
			System.out.println();
		}
		return this;
	}
	
	public CPrint a(Attribute attr) {
		if (color) coPrint.setAttribute(attr);
		return this;
	}
	
	public CPrint f(FColor color) {
		if (this.color) coPrint.setForegroundColor(color);
		return this;
	}
	
	public CPrint b(BColor color) {
		if (this.color) coPrint.setBackgroundColor(color);
		return this;
	}
	
	public CPrint clr() {
		if (color) coPrint.clear();
		return this;
	}
	
	public CPrint updateLabel() {
		return this.clr().a(Attribute.BOLD).f(FColor.CYAN).b(BColor.NONE);
	}

	public CPrint updateMsg() {
		return this.clr().a(Attribute.NONE).f(FColor.CYAN).b(BColor.NONE);
	}

	public CPrint info() {
		return this.clr().a(Attribute.DARK).f(FColor.CYAN).b(BColor.NONE);
	}
	
	public CPrint statusLabel() {
		return this.clr().a(Attribute.NONE).f(FColor.CYAN).b(BColor.NONE);
	}
	
	public CPrint normData() {
		 return this.clr().a(Attribute.BOLD).f(FColor.GREEN).b(BColor.NONE);
	}

	public CPrint dlData() {
		return this.clr().a(Attribute.BOLD).f(FColor.YELLOW).b(BColor.NONE);
	}
	
	public CPrint unitLabel() {
		return this.clr().a(Attribute.NONE).f(FColor.WHITE).b(BColor.NONE);
	}
	
	public CPrint textData() {
		return this.clr().a(Attribute.DARK).f(FColor.WHITE).b(BColor.NONE);
	}
	
	public CPrint headers() {
		return this.clr().f(FColor.CYAN).a(Attribute.LIGHT).b(BColor.NONE);
	}
}
