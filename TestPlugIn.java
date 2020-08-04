package com.gmail.ham.testplugin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/*

------------------------------------------------------------------
※使用前に必ずsetTpHostコマンドを使用し、kunさんをTpHostに設定すること※
※やっておかないとバグります※
------------------------------------------------------------------


＜kunさんへのtpや個々のチャットによって有名、無名を機械的に判断しようという試み＞
・kunさんへのtpは+100、チャットは+5、ログインは+1
・数が大きいほど有名となる。(famousListコマンドでチャットに値をリスト表示可能)
　（有名・無名の基準を増やしたいならば、新たにコマンド作ってください！
　　map_of_famouslevelにプレーヤー名をキーとして、元の値をインクリメントすればできますので。）
・chooseFamous,chooseNotFamousコマンドを使うと、それぞれランダムで有名、無名を選出可能。

想定する企画としては、
・一定時間ごとに無名を数人ずつban→残った何人かが勝ち
・一定時間ごとに有名と無名をひとりずつban→残った何人かが勝ち
くらいでしょうか...。

正直思いつき半分でやったので活用方法まで考えられていませんm(__)m
企画よりは、普段の有名・無名の可視化に役立つプラグインとなっています。
*/

public final class TestPlugIn extends JavaPlugin implements Listener {
    FileConfiguration config;
    private Map<String, Integer> map_of_famouslevel = new HashMap<String, Integer>();
    //プレーヤーの有名度(tpホストは除く)
    private String TpHost;//テレポートのホスト(kunさんを想定)

    private final Integer LOGIN_INCREMANT = 1;
    private final Integer CHAT_INCREMENT = 5;
    private final Integer TP_INCREMENT = 100;

    @EventHandler
    public void PlayerLogin(PlayerLoginEvent event){//ログインすることで有名度がup
        incrementFamouslevel(event.getPlayer().getName(), LOGIN_INCREMANT);
    }

    @EventHandler
    public void PlayerChat(AsyncPlayerChatEvent event){//チャットを打つことで有名度がup
        incrementFamouslevel(event.getPlayer().getName(), CHAT_INCREMENT);
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        getServer().getPluginManager().registerEvents(this,this);

        try {
            // config.ymlが存在しない場合はファイルに出力します。
            saveDefaultConfig();
            // config.ymlを読み込みます。
            config = getConfig();

            //MapFamouslevel,TpHostパスが存在しない場合は追加
            if(!config.contains("MapFamouslevel")){
                config.createSection("MapFamouslevel", map_of_famouslevel);
            }
            if(!config.contains("TpHost")){
                config.createSection("TpHost");
                config.set("TpHost", "NO_SETTING");
            }

            Iterator<OfflinePlayer> wlp_i = Bukkit.getWhitelistedPlayers().iterator();
            Iterator<OfflinePlayer> op_i = Bukkit.getOperators().iterator();
            String str;

            while(wlp_i.hasNext()){//0でホワイトリストメンバーを初期化
                str = wlp_i.next().getName();
                if(config.contains("MapFamouslevel." + str)){//すでにconfig内に存在する場合
                    map_of_famouslevel.put(str, config.getInt("MapFamouslevel." + str));
                } else {//まだconfig内に存在しない場合
                    //そのセクションを追加し、0にしておく
                    config.createSection("MapFamouslevel." + str);
                    config.set("MapFamouslevel." + str, 0);
                    map_of_famouslevel.put(str, 0);
                }
            }
            if(op_i.hasNext()){
                str = op_i.next().getName();
                if(config.getString("TpHost") != "NO_SETTING"){//すでにconfig内に存在する場合
                    TpHost = config.getString("TpHost");
                }else {//まだconfig内に存在しない場合
                    //デフォルトでop権限を持つ一人をTpHostとする
                    config.set("TpHost", str);
                    TpHost = str;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        config.set("MapFamouslevel",map_of_famouslevel);
        config.set("TpHost",TpHost);
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        //sender...実行者
        //command...コマンド

        if(command.getName().equalsIgnoreCase("setTpHost")){
            if(args.length != 1){
                //onCommandでfalseを戻すと、plugin.ymlのusageに設定したメッセージを
                //コマンド実行者の画面に表示します。
                return false;
            }

            Player target = getServer().getPlayerExact(args[0]);

            if(target == null){
                //指定プレーヤー名が存在しないとき
                return false;
            }else{
                //プレーヤーをセット
                TpHost = target.getName();
                getServer().broadcastMessage("[TpHostを " + TpHost + " に設定しました]");
            }

            return true;
        }
        else if(command.getName().equalsIgnoreCase("getTpHost")){
            if(args.length != 0){
                //onCommandでfalseを戻すと、plugin.ymlのusageに設定したメッセージを
                //コマンド実行者の画面に表示します。
                return false;
            }

            if(TpHost == "NO_SETTING") {
                getServer().broadcastMessage("[現在TpHostは設定されていません]");
            } else {
                getServer().broadcastMessage("[現在TpHostは " + TpHost + " です]");
            }

            return true;
        }else if (command.getName().equalsIgnoreCase("tp")){//TpHostへのtpによって有名度がup
            // tp A B で AがBへtpされる

            if(args.length != 2){
                return false;
            }

            Player player0 = Bukkit.getPlayerExact(args[0]);//転送元
            Player player1 = Bukkit.getPlayerExact(args[1]);//転送先

            if(player1 == null) {//第2引数がプレーヤーで無ければ
                if (args[1].equals("@p")) {
                    try{
                        player1 = (Player)sender;
                    }
                    catch(Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    return false;
                }
            }
            if(player0 == null){//第1引数がプレーヤーで無ければ
                if(args[0].equals("@a")){
                    Iterator<OfflinePlayer> wlp_i = Bukkit.getWhitelistedPlayers().iterator();
                    Player tmp_p;

                    while(wlp_i.hasNext()){
                        tmp_p = Bukkit.getPlayerExact(wlp_i.next().getName());
                        if(tmp_p != null){
                            tmp_p.teleport(player1);
                            getServer().broadcastMessage("[" + tmp_p.getName() +"を" + player1.getName() + "へテレポートしました]");

                            if((player1.getName()).equals(TpHost)){
                                incrementFamouslevel(tmp_p.getName(),TP_INCREMENT);
                            }
                        }
                    }
                } else{
                    return false;
                }
            } else {
                player0.teleport(player1);
                getServer().broadcastMessage("[" + player0.getName() +"を" + player1.getName() + "へテレポートしました]");

                if((player0.getName()).equals(TpHost)) {
                    incrementFamouslevel(player1.getName(),TP_INCREMENT);}
                else if ((player1.getName()).equals(TpHost)){
                    incrementFamouslevel(player0.getName(),TP_INCREMENT);
                }
            }
        }

        //全体のfamouslevelを扱う関数群
        if (command.getName().equalsIgnoreCase("chooseFamous")){
            long rnd = Math.round(Math.random() * (map_of_famouslevel.size() - 1.0) / 2.0);
            int count = 0;
            for(Map.Entry<String, Integer> entry : sortedMapOfFamouslevel()) {
                if(count == (int)rnd) {
                    getServer().broadcastMessage("[有名人として" + entry.getKey() + " : " + entry.getValue() + "が選ばれました]");
                    return true;
                }
                count++;
            }
        }else if (command.getName().equalsIgnoreCase("chooseNotFamous")){
            long rnd = Math.round(Math.random() * (map_of_famouslevel.size() - 1.0) / 2.0);
            int count = 0;
            for(Map.Entry<String, Integer> entry : sortedMapOfFamouslevel()) {
                if(count == (int)(rnd + map_of_famouslevel.size() / 2.0)) {
                    getServer().broadcastMessage("[無名として" + entry.getKey() + " : " + entry.getValue() + "が選ばれました]");
                    return true;
                }
                count++;
            }
        } else if (command.getName().equalsIgnoreCase("FamousList")){
            // ループで要素順に値を取得する
            getServer().broadcastMessage("[有名度リスト]------------");
            for(Map.Entry<String, Integer> entry : sortedMapOfFamouslevel()) {
                getServer().broadcastMessage(entry.getKey() + " : " + entry.getValue());
            }
            getServer().broadcastMessage("----------------------");
        }

        return true;
    }

    //自作関数
    //famouslevelをインクリメントする関数
    private void incrementFamouslevel(String str, Integer increment){
        Integer i = map_of_famouslevel.get(str);
        map_of_famouslevel.replace(str,i + increment);//tp回数に応じたインクリメント
        //getServer().broadcastMessage("[" + str + "の" + i + "が" + map_of_famouslevel.get(str) + "へインクリメントされました]");
    }

    //famouslevelのマップを値（famouslevel）で降順にソートし、Listにして返す。
    //使用方法は"Famouslist"関数などを参照のこと
    private List<Map.Entry<String, Integer>> sortedMapOfFamouslevel(){
        // Map.Entryのリストを作成する
        List<Map.Entry<String, Integer>> list_entries = new ArrayList<Map.Entry<String, Integer>>(map_of_famouslevel.entrySet());

        // 比較関数Comparatorを使用してMap.Entryの値を比較する（降順）
        Collections.sort(list_entries, new Comparator<Map.Entry<String, Integer>>() {
            //compareを使用して値を比較する
            public int compare(Map.Entry<String, Integer> obj1, Map.Entry<String, Integer> obj2)
            {
                //降順
                return obj2.getValue().compareTo(obj1.getValue());
            }
        });

        return list_entries;
    }

}
