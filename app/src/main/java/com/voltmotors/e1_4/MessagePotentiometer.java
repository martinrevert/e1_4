

package com.voltmotors.e1_4;

public class MessagePotentiometer {
	public int percentage = 0;
	
	MessagePotentiometer(int p) {
		//Do some boundary checking
		if(p < 0) {
			p = 0;
		}
		
		if(p > 100) {
			p = 100;
		}
		
		percentage = p;
	}
}
