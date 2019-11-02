package edu.depaul.cleanSweep.controlSystem;

import edu.depaul.cleanSweep.cell.SurfaceType;
import edu.depaul.cleanSweep.diagnostics.PowerConsumptionLog;
import edu.depaul.cleanSweep.floorPlan.*;

import org.javatuples.Pair;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class Cleaner {

	private static final double MAX_BATTERY_POWER = 250;
	private static final int MAX_DIRT_CAPACITY = 50;

	private double currBattery;
	private int currDirtCapacity;
	private boolean atCapacity;
	private boolean almostAtCapacity;
	private char headingTowards = 'N';
	private String currStatus = new String("No status yet");
	private static PowerConsumptionLog pcl;
	private FloorTile currNode;
	private FloorTile prevNode;

	// The vacuumbag is a list, with each node representing a "cleaning" of a tile
	// Each clean appends a Pair representing the amount of dirt cleaned, as well the surface type
	// In order to traverse through the history, start at the head, and work downward
	private List<Pair<Integer, TileType>> vacuumBag = new LinkedList<Pair<Integer, TileType>>();

	public Cleaner() throws IOException{
		pcl = PowerConsumptionLog.getInstance();
		currBattery = MAX_BATTERY_POWER;
		currDirtCapacity = MAX_DIRT_CAPACITY;
	private ArrayList<FloorTile> cleanerHistory = new ArrayList<FloorTile>();

	private CustomLinkedList currentMap = new CustomLinkedList();

	public Cleaner(){

	}

	public Cleaner(double battery, int dirtCapacity, FloorTile node) {
		currBattery = battery;
		currDirtCapacity = dirtCapacity;
		currNode = node;
	}
	
	public void setCurrNode(FloorTile n) {
		currNode = n;
		currentMap.insert(n._x, n._y);
		//cleanerHistory.add(copyTile(this.currNode));
	}

	
	public FloorTile getCurrNode() {
		return currNode != null ? currNode : new FloorTile(0, 0);
	}


	/*
	 * According to the current heading direction, first check if the corresponding side is blocked,
	 * and then move to the next cell.
	 */
	public boolean moveAhead() {
		boolean flag = false;
		double averagePowerCost;
		
		switch(this.headingTowards) {
			//todo - add surfacetype to history once surfacetype is a member of FLoorTile see:
			// https://trello.com/c/UAVH322u/6-floor-plan-manager-as-a-user-i-expect-the-floor-plan-system-to-identify-different-types-of-cells-and-process-represent-them-acco

			case 'N':
				if(this.currNode.north != null && this.currNode.north.getAccessable()) {
					this.prevNode = currNode;
					this.currNode = currNode.north;
					//cleanerHistory.add(copyTile(this.currNode));
					this.currentMap.insert(copyFloorTile(this.currNode));
					flag = true;
				}
				break;
			case 'S':
				if(this.currNode.south != null && this.currNode.south.getAccessable()) {
					this.prevNode = currNode;
					this.currNode = currNode.south;
					this.currentMap.insert(copyFloorTile(this.currNode));
					flag=true;
				}
				break;
			case 'W':
				if(this.currNode.west != null && this.currNode.west.getAccessable()) {
					this.prevNode = currNode;
					this.currNode = currNode.west;
					this.currentMap.insert(copyFloorTile(this.currNode));
					flag = true;
				}
				break;
			case 'E':
				if(this.currNode.east != null && this.currNode.east.getAccessable()) {
					this.prevNode = currNode;
					this.currNode = currNode.east;
					this.currentMap.insert(copyFloorTile(this.currNode));
					flag = true;
				}

				break;
		}
		// get average battery cost, log it, and subtract from battery total
		averagePowerCost = (this.prevNode.getBatteryConsumption() + this.currNode.getBatteryConsumption()) / 2;
		pcl.logPowerUsed("Movement", prevNode, currNode, currBattery, averagePowerCost);
		this.currBattery -= averagePowerCost;
		
		if (this.currNode.getChargeStation()) {
			this.currBattery = MAX_BATTERY_POWER;
			pcl.logPowerUsed("Charging", prevNode, currNode, currBattery, 0);
		}
		
		System.out.println(printCoordinate());
		return flag;
	}
	/*
	 * For moving left, right or back, the heading direction will change correspondingly, and then move forward.
	 */
	public void moveLeft() {
		switch(this.headingTowards) {
			case 'N':
				this.headingTowards = 'W';
				this.moveAhead();
				break;
			case 'S':
				this.headingTowards = 'E';
				this.moveAhead();
				break;
			case 'W':
				this.headingTowards = 'S';
				this.moveAhead();
				break;
			case 'E':
				this.headingTowards = 'N';
				this.moveAhead();
				break;
		}
		//change current battery level
	}

	public void moveRight() {
		switch(this.headingTowards) {
			case 'N':
				this.headingTowards = 'E';
				this.moveAhead();
				break;
			case 'S':
				this.headingTowards = 'W';
				this.moveAhead();
				break;
			case 'W':
				this.headingTowards = 'N';
				this.moveAhead();
				break;
			case 'E':
				this.headingTowards = 'S';
				this.moveAhead();
				break;
		}
		//change current battery level
	}

	public void moveBack() {
		switch(this.headingTowards) {
			case 'N':
				this.headingTowards = 'S';
				this.moveAhead();
				break;
			case 'S':
				this.headingTowards = 'N';
				this.moveAhead();
				break;
			case 'W':
				this.headingTowards = 'E';
				this.moveAhead();
				break;
			case 'E':
				this.headingTowards = 'W';
				this.moveAhead();
				break;
		}
		
		//change current battery level
	}


	// Check for "cleanliness" of current surface. Clean if need be and update capacity
	public void cleanSurface(FloorTile currentTile) {
		TileType surfacedCleaned = currentTile.getSurfaceType();

		// Cell is currently clean. No need to do anything
		if(currentTile.getClean() == true) {
			return;
		}

		// Cell is not clean, clean it, update bag, and change cell state
		Integer spaceLeft = MAX_DIRT_CAPACITY - getCurrentBagSize();
		// Check for space
		if(spaceLeft <= 0 || atCapacity) {
			// Can't hold any more. Do not clean cell
			return;
		}
		else {
			// Add to vaccumbag
			currentTile.decreaseDirtAmount(); //enforces 1 unit at a time
			pcl.logPowerUsed("Cleaning", currentTile, currentTile, currBattery, currentTile.getBatteryConsumption());
			currBattery -= currentTile.getBatteryConsumption();
			vacuumBag.add(new Pair<Integer, TileType>(1, currentTile.getSurfaceType()));
			checkBagSize();
		}
	}

	private void checkBagSize() {
		assert(getCurrentBagSize() <= MAX_DIRT_CAPACITY);
		atCapacity = (getCurrentBagSize() == MAX_DIRT_CAPACITY);
		almostAtCapacity = (getCurrentBagSize() >= 35);
		if(atCapacity) {
			currStatus = "The Clean Sweep is out of space for dirt!";
		}
		else if(almostAtCapacity) {
			currStatus = "The Clean Sweep's current bag size is: " + getCurrentBagSize();
		}
	}

	public boolean isAtCapacity() {
		return atCapacity;
	}

	public boolean isAlmostAtCapacity() { return almostAtCapacity; }
	
	public String getCleanerStatus () {
		return currStatus;
	}
	public Integer getCurrentBagSize(){
		return vacuumBag.stream().mapToInt(record -> {
			return record.getValue0();
		}).sum();
	}

	public String printCoordinate() {
		return "My coordinate is " + this.currNode._x +", "+ this.currNode._y;
	}

	public void changeHeading(char h){
		headingTowards = h;
	}

	public List<FloorTile> getCleanerHistory(){
		return cleanerHistory;
	}

	public char getHeadingTowards() {
		return headingTowards;
	}

	public CustomLinkedList getCurrentMap(){ return currentMap; }

	private FloorTile copyFloorTile(FloorTile tile){
		var temp = new FloorTile(tile._y, tile._x, tile.getUnitsOfDirt(), tile.getSurfaceType());
		temp.setChargeStation(tile.getChargeStation());
		return temp;
	}
	
	public char getHeading() {return this.headingTowards;}
	
	public double getBatteryPower() {
		return currBattery;
	}
	
}