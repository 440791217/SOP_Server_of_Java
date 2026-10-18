package org.jsut.modules.resource.service;
import java.net.InetAddress;
import java.net.UnknownHostException;
import static org.jsut.modules.resource.service.ResourceSupport.bad;
/** Numeric addresses only: never resolves hostnames or probes devices. */
public final class StationIp {
 private StationIp(){}
 public static String normalize(String raw){
  if(raw==null||raw.isBlank())throw bad("请填写工位IP地址");
  String value=raw.trim();
  if(value.contains(":")){
   if(!value.matches("[0-9A-Fa-f:.]+"))throw bad("工位IP地址格式不正确");
   try{return InetAddress.getByName(value).getHostAddress().toLowerCase(java.util.Locale.ROOT);}
   catch(UnknownHostException e){throw bad("工位IP地址格式不正确");}
  }
  String[] parts=value.split("\\.",-1);if(parts.length!=4)throw bad("工位IP地址格式不正确");
  for(int i=0;i<4;i++){if(!parts[i].matches("0|[1-9][0-9]{0,2}")||Integer.parseInt(parts[i])>255)throw bad("工位IP地址格式不正确");}
  return String.join(".",parts);
 }
}
