package com.ese2013.mub;

import android.view.View;
import android.view.View.OnClickListener;

import com.ese2013.mub.model.Mensa;

public class AddressTextListener implements OnClickListener{
private Mensa mensa;
private IAdapter adapt;


	
	public AddressTextListener(Mensa mensa, IAdapter adapt) {
		this.mensa = mensa;
		this.adapt = adapt;
	}
	@Override
	public void onClick(View viewIn) {
		
		adapt.sendListToMenusIntent(mensa);
	}
}
