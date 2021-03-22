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
        this.entries.add(entry); 
    }

    /* Given the (ip, mask), find the entry. Delete it if outdated. */
    public DVEntry findEntry(int ip, int mask){
        for (DVEntry e : this.entries){
            if(e.ip == ip && e.mask == mask){
                if(e.isSelf() == false && System.currentTimeMillis() - e.initTime > TIMEOUT){
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
            if(e.ip == ip && e.mask == mask){
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
            if(e.ip == f.ip && e.mask == f.mask){
                e.setMetric(f.metric);
                e.renew();
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
                assert this.entries.remove(e) == true;
            }
        }
    }

    /* Transform current distance vector to RIPv2 packet's entries */
    public RIPv2 toRIPv2(){
        RIPv2 r = new RIPv2();
        for (DVEntry e : this.entries){
            r.addEntry(new RIPv2Entry(e.ip, e.mask, e.metric));
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
            sb.append("IP: " + IPv4.fromIPv4Address(e.ip) + ";\t" + IPv4.fromIPv4Address(e.mask) + ";\t" + e.metric + ";\t" + e.isSelf() + ";\t" + IPv4.fromIPv4Address(e.nexthop) + "\n");
        }
        return sb.toString();
    }
}
