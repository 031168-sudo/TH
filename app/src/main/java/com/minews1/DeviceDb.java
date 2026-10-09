package com.minews1;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class DeviceDb {
    public static class Device {
        public String id, name, type;
        public Device(String i, String n, String t){id=i;name=n;type=t;}
    }
    // Derived from the advertisement payload by Ble.xiaomiMac(), not the radio address.
    private static final String XIAOMI_MAC = "6B:95:30:34:2D:58";
    private final SharedPreferences prefs;
    public DeviceDb(Context c){prefs=c.getSharedPreferences("devices",Context.MODE_PRIVATE);}
    public List<Device> all(){
        List<Device> out=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(prefs.getString("list","[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);out.add(new Device(o.getString("id"),o.getString("name"),o.getString("type")));
            }
        }catch(Exception ignored){}
        return out;
    }
    public void ensureDevices(){
        if(find("78:05:41:06:91:BA")==null)add("78:05:41:06:91:BA","Minew S1","minew");
        if(find("78:05:41:01:E6:36")==null)add("78:05:41:01:E6:36","Minew WTS300","minew_wts300");
        if(find(XIAOMI_MAC)==null)add(XIAOMI_MAC,"Xiaomi LYWSDCGQ/01ZM","xiaomi");
    }
    public void removeUnlisted(){
        for(Device d:all()){
            if(!d.id.equalsIgnoreCase("78:05:41:06:91:BA")
                    &&!d.id.equalsIgnoreCase("78:05:41:01:E6:36")
                    &&!d.id.equalsIgnoreCase(XIAOMI_MAC)) remove(d.id);
        }
    }
    public Device find(String id){for(Device d:all())if(d.id.equalsIgnoreCase(id))return d;return null;}
    public void add(String id,String name,String type){
        if(find(id)!=null)return;
        try{
            JSONArray a=new JSONArray(prefs.getString("list","[]"));
            JSONObject o=new JSONObject();o.put("id",id.toUpperCase());o.put("name",name);o.put("type",type);a.put(o);
            prefs.edit().putString("list",a.toString()).apply();
        }catch(Exception ignored){}
    }
    public void remove(String id){
        try{
            JSONArray src=new JSONArray(prefs.getString("list","[]")), dst=new JSONArray();
            for(int i=0;i<src.length();i++){JSONObject o=src.getJSONObject(i);if(!id.equalsIgnoreCase(o.getString("id")))dst.put(o);}
            prefs.edit().putString("list",dst.toString()).apply();
        }catch(Exception ignored){}
    }
}
