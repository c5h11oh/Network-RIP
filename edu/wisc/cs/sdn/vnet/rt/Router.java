package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.UDP;


import java.util.*;
import java.lang.System;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;

	/** ARP cache for the router */
	private ArpCache arpCache;
	//distance vector table 
	/*
	protected short addressFamily;
    protected short routeTag;
	protected int address;
	protected int subnetMask;
	protected int nextHopAddress;
	protected int metric;
	*/
	// private List<RIPv2Entry> dvTable;
	//key is a list of IP address and mask; 
	//value is a list of object: [int metrics, long initTime, boolean self, int nexthop]
	private HashMap< List<Integer>, ArrayList<Object> > dvTable;

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		this.dvTable = new HashMap< List<Integer>, ArrayList<Object> >();
	}

	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }

	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}

		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}

	/*
	This method sends RIP request to all neighbors 
	*/
	public void floodRIPRequest(){

		for (Iface iface : this.interfaces.values()){
			RIPv2 initReq = new RIPv2();
			initReq.setCommand(RIPv2.COMMAND_REQUEST);
			encapToFlood( iface,  initReq);
		}

	}

	/*
	This function initialize the router's route table when the router does not have a loaded route table 
	It sends RIP request to all neighbors 
	It will then start to send unsolicited response to all neighbors every 10s
	*/
	public void initializeRouteTable(){
		// Debugging
		System.out.println("initializeRouteTable called");
		// ! Debugging

		for (Iface iface : this.interfaces.values()){	
			//init route table 
			this.routeTable.insert(iface.getIpAddress(), 0, iface.getSubnetMask(), iface);
			
		}
		
		//send RIP request out all ifaces 
		floodRIPRequest(); 
		//RIPv2Entry(int address, int subnetMask, int metric)
		long updateTime = System.currentTimeMillis() + 10;
		
		while(dvTable != null && dvTable.size() !=0 ){
			// Debugging
			System.out.println("send out RIPv2 info");
			// !Debugging

			//send update per 10 s
			if(System.currentTimeMillis() >= updateTime){ 
				//send the RIP packet to the neighbor 
				for (Iface iface : this.interfaces.values()){	
					List<RIPv2Entry> updateLs = new ArrayList<RIPv2Entry>(); 

					for(Map.Entry<List<Integer>, ArrayList<Object>> e: dvTable.entrySet() ){
						ArrayList<Object> values = e.getValue(); 
						RIPv2Entry updateE; 
						//check if still have ttl
						if(((int)values.get(1)+30)< System.currentTimeMillis()){
							//check if need poison reverse 
							if((int)values.get(3) == iface.getIpAddress()){ // TODO: should be the receiver's IP address?
								updateE = new RIPv2Entry(e.getKey().get(0),e.getKey().get(1), 16 );
							}else{
								updateE = new RIPv2Entry(e.getKey().get(0),e.getKey().get(1), (int) values.get(0)); 
							}
							updateLs.add(updateE); 

						}else{ //no ttl 
							if((int)values.get(3) != iface.getIpAddress()){
								//delete the entry
								dvTable.remove(e.getKey()); 
							}
						}

					}

					//forward with this RIPEntry list 
					RIPv2 updatePkt = new RIPv2();
					updatePkt.setCommand(RIPv2.COMMAND_RESPONSE);
					updatePkt.setEntries((List<RIPv2Entry>) updateLs); 
					encapToFlood(iface, updatePkt); 
				}

				updateTime = System.currentTimeMillis() +10; 
			}
		}
		
	}

	/*
	This method encapsulates and sends a RIPv2 packet through a specific interface 
	// Do we "forward" a RIPv2 packet? -> use sendPacket instead of forwardIpPacket?
	*/
	public void encapToFlood(Iface iface, RIPv2 rip){
		System.out.println("encapToFlood called");
		UDP udp = new UDP();
		udp.setPayload((IPacket)rip); 
		udp.setSourcePort(UDP.RIP_PORT);
		udp.setDestinationPort(UDP.RIP_PORT); 

		IPv4 initIPv4 = new IPv4();
		initIPv4.setPayload((IPacket)udp); 
		initIPv4.setProtocol(IPv4.PROTOCOL_UDP);
		initIPv4.setDestinationAddress("224.0.0.9");
		initIPv4.setSourceAddress(iface.getIpAddress()); 

		Ethernet eth = new Ethernet();
		eth.setPayload(initIPv4);
		eth.setSourceMACAddress(iface.getMacAddress().toBytes());
		eth.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
		eth.setEtherType(Ethernet.TYPE_IPv4); 
		
		System.out.println("Sending RIPv2 packet.");
		System.out.println("RIPv2 packet info are:\n" + eth);
		this.sendPacket( eth,  iface); // TODO: should it be sendPacket?
		System.out.println("RIPv2 packet sent.\n");

	}

	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}

		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
				etherPacket.toString().replace("\n", "\n\t"));

		/********************************************************************/
		/* TODO: Handle packets                                             */

		switch(etherPacket.getEtherType())
		{
		case Ethernet.TYPE_IPv4:
			this.handleIpPacket(etherPacket, inIface);
			break;
		// Ignore all other packet types, for now
		default:
			System.out.println("Not an IPv4! Ignore.");
			break;
		}

		/********************************************************************/
	}
	
	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("handleIpPacket");
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ 
			System.out.println("Err: notIPv4");
			return; }

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		System.out.println("Handle IP packet");

		// Verify checksum
		short origCksum = ipPacket.getChecksum();
		ipPacket.resetChecksum();
		byte[] serialized = ipPacket.serialize();
		ipPacket.deserialize(serialized, 0, serialized.length);
		short calcCksum = ipPacket.getChecksum();
		if (origCksum != calcCksum)
		{ 
			System.out.println("Err: checksum");
			return; }

		// Check TTL
		ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
		if (0 == ipPacket.getTtl())
		{ 
			System.out.println("Err: ttl=0");
			return; }

		// Reset checksum now that TTL is decremented
		ipPacket.resetChecksum();

		// Check if packet is destined for one of router's interfaces
		for (Iface iface : this.interfaces.values())
		{
			if (ipPacket.getDestinationAddress() == iface.getIpAddress())
			{ 
				System.out.println("Err: router interface");
				return; }
		}

		// Check if the packet is an RIPv2 packet
		if (ipPacket.getProtocol() == IPv4.PROTOCOL_UDP && ipPacket.getDestinationAddress() == IPv4.toIPv4Address("224.0.0.9")){
			handleRIPPacket((RIPv2)ipPacket.getPayload().getPayload(), ipPacket.getSourceAddress());
			return; // Do not forward
		}

		// Do route lookup and forward
		System.out.println("handleIpPacket: forwardingIpPacket");
		this.forwardIpPacket(etherPacket, inIface);
	}

	private void handleRIPPacket(RIPv2 rip, int sourceAddr){
		// Debugging
		System.out.println("called handleRIPPacket. \nrip packet content: " + rip + "\nsourceAddr: " + IPv4.fromIPv4Address(sourceAddr));
		// !Debugging

		for (RIPv2Entry e: rip.getEntries()){
			
			ArrayList<Integer> ls = new ArrayList<Integer>();
			ls.add(e.getAddress()); 
			ls.add(e.getSubnetMask());

			ArrayList<Object> v = new ArrayList<Object>();
			v.add(e.getMetric()+1); //updated path cost 
			v.add(System.currentTimeMillis()); //time stamp
			v.add(false); 
			v.add(sourceAddr);

			if(dvTable.containsKey(ls)){
				if((int)dvTable.get(ls).get(0) > (int)v.get(0)){
					dvTable.put(ls, v);
				}
			}
			else{
				dvTable.put(ls, v);
			}
			
		}
	}

	private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
		System.out.println("Forward IP packet");

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		int dstAddr = ipPacket.getDestinationAddress();

		// Find matching route table entry 
		RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

		// If no entry matched, do nothing
		if (null == bestMatch)
		{ 
			System.out.println("Err: bestmatch not found");
			return; }

		// Make sure we don't sent a packet back out the interface it came in
		Iface outIface = bestMatch.getInterface();
		if (outIface == inIface)
		{ 
			System.out.println("Err: outeqin");
			return; }

		// Set source MAC address in Ethernet header
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

		// If no gateway, then nextHop is IP destination
		int nextHop = bestMatch.getGatewayAddress();
		if (0 == nextHop)
		{ nextHop = dstAddr; }

		// Set destination MAC address in Ethernet header
		ArpEntry arpEntry = this.arpCache.lookup(nextHop);
		if (null == arpEntry)
		{ 
			System.out.println("Err: arpentry");
			return; }
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

		this.sendPacket(etherPacket, outIface);
	}
}
