package edu.wisc.cs.sdn.vnet.rt;

import java.lang.System;
import java.util.*;
import net.floodlightcontroller.packet.*;

public class DV {
    protected static final int TIMEOUT = 30000; // in millisecond
    protected List<DVEntry> entries;

    public DV(){
        super();
        this.entries = new LinkedList<DVEntry>();
    }

    public void setEntries(List<DVEntry> entries){
        this.entries = entries;
    }

    public List<DVEntry> getEntries(){
        return this.entries;
    }

    public int size(){
        return entries.size();
    }

    /* 
        Add entry to the distance vector entry list.
        Do note: This method does not check if there are duplicates in the table. It is recommended to call "findEntry" and deal with (ip, mask) exist condition properly beforehand.
     */
    public void addEntry(DVEntry entry)
    { 
        System.out.println("DV: addEntry: \n\t" + entry.toString());
        this.entries.add(entry); 
    }

    /* Given the (ip, mask), find the entry. Delete it if outdated. */
    public DVEntry findEntry(int ip, int mask){
        for (DVEntry e : this.entries){
            if(e.getIP() == ip && e.getMask() == mask){
                if(e.isSelf() == false && System.currentTimeMillis() - e.getInitTime() > TIMEOUT){
                    System.out.println("DV: findEntry: remove entry: \n\t" + e.toString());
                    assert this.entries.remove(e) == true;
                    return null;
                }
                else return e;
            }
        }
        return null;
    }

    /*
        Given (ip, mask), renew the entry's "initTime" to current time
    */
    public boolean renewEntry(int ip, int mask){
        for (DVEntry e : this.entries){
            if(e.getIP() == ip && e.getMask() == mask){
                e.renew();
                return true;
            }
        }
        return false;
    }
    
    /*
        Given DVEntry, replace the entry with new route (metrics, nexthop, initTime)
    */
    public boolean replaceEntry(DVEntry f){
        for(DVEntry e : this.entries){
            if(e.getIP() == f.getIP() && e.getMask() == f.getMask()){
                e.setMetric(f.getMetric());
                e.setNextHop(f.getNexthop());
                e.renew();
                System.out.println("DV: replace entry: \n\t" + e.toString());
                return true;
            }
        }
        return false;
    }

    /*
        Remove entries that are expired. Never delete the self ones.
    */
    public void cleanUp(){
        long currentTime = System.currentTimeMillis();
        for (DVEntry e : this.entries){
            if (e.isSelf() == false && currentTime - e.initTime > TIMEOUT){
                System.out.println("DV: cleanUp: remove entry: \n\t" + e.toString());
                assert this.entries.remove(e) == true;
            }
        }
    }

    /* Transform current distance vector to RIPv2 packet's entries */
    public RIPv2 toRIPv2(){
        RIPv2 r = new RIPv2();
        for (DVEntry e : this.entries){
            RIPv2Entry ripE = new RIPv2Entry(e.getIP(), e.getMask(), e.getMetric()); 
            ripE.setNextHopAddress(e.getNexthop()); 
            r.addEntry(ripE);
            System.out.print("In to RIPv4, add: ");
            System.out.print(ripE); 
            System.out.println();
        }
        return r;
    }

    /* printing */
    @Override
    public String toString(){
        StringBuffer sb = new StringBuffer();
        sb.append("Distance Vector contains:\n");
        sb.append("IP\t\tMask\t\tMetric\t\tSelf\t\tNexthop\n");
        sb.append("=========================================\n");
        for (DVEntry e: entries){
            sb.append("IP: " + IPv4.fromIPv4Address(e.getIP()) + ";\t" + IPv4.fromIPv4Address(e.getMask()) + ";\t" + e.getMetric() + ";\t" + e.isSelf() + ";\t" + IPv4.fromIPv4Address(e.getNexthop()) + "\n");
        }
        return sb.toString();
    }
}
