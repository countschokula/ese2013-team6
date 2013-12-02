package com.ese2013.mub.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a list of menus served at a specific day (in one mensa). There must
 * be at least one menu in a DailyMenuplan (except while creation).
 */
public class DailyMenuplan {
	private ArrayList<Menu> menus = new ArrayList<Menu>();

	/**
	 * Adds a menu to this menu plan.
	 * 
	 * @param menu
	 *            Menu to be added. Must not be null.
	 */
	public void add(Menu menu) {
		menus.add(menu);
	}

	/**
	 * Returns the list of menus.
	 * 
	 * @return List containing the menus.
	 */
	public List<Menu> getMenus() {
		return menus;
	}

	/**
	 * Returns the date of the menus as strings. Uses the invariant that all
	 * menus have the same date.
	 * 
	 * @return String containing the date as a String. This String should only
	 *         be used for visual output, as it depends on the Locale settings.
	 */
	public String getDateString() {
		return menus.get(0).getDateString();
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (other instanceof DailyMenuplan) {
			DailyMenuplan otherPlan = (DailyMenuplan) other;
			return otherPlan.getMenus().equals(this.menus);
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return menus.hashCode();
	}

	@Override
	public String toString() {
		String result = "Menus for " + getDateString() + "\n";
		for (Menu m : menus) {
			result += m.toString();
			result += "\n";
		}
		return result;
	}
}