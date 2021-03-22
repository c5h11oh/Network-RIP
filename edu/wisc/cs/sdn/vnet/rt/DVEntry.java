package edu.wisc.cs.sdn.vnet.rt;

import java.lang.System;

//key is a list of IP address and mask; 
//value is a list of object: [int metric, long initTime, boolean self, int nexthop]

public class DVEntry {
    protected int ip;
    protected int mask;
    protected int metric;
    protected long initTime;
    protected boolean self;
    protected int nexthop;

    public DVEntry(){}

    public DVEntry(int ip, int mask, int metric, boolean self, int nexthop){
        this.ip = ip;
        this.mask = mask;
        this.metric = metric;
        this.self = self;
        this.nexthop = nexthop;
        this.initTime = System.currentTimeMillis();
    }

    // IP, mask
    public int getIP(){ return this.ip; }
    public int getMask(){ return this.mask; }

    // Metric
    public int getMetric(){ return this.metric; }
    public void setMetric(int metric){
        this.metric = metric;
    }

    // Self
    public boolean isSelf(){
        return this.self;
    }

    //next hop 
    public int getNexthop(){
        return this.nexthop; 
    }
    
    // Inittime
    public long getInitTime(){
        return this.initTime;
    }
    public void renew(){
        this.initTime = System.currentTimeMillis();
    }

    
}
