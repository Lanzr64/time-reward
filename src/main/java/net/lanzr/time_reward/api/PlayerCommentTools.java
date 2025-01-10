package net.lanzr.time_reward.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openjdk.nashorn.api.tree.ReturnTree;

import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.io.Reader;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

public class PlayerCommentTools {
    static final String PATH = "lzFiles";
    static final String COMMENT_RECORD_PATH = PATH + "/comment-record.json";
    static final int[] COMMENT_LEVEL = {1,3,6,12,24};

    public static int getPlayerComment(String name, CommentInfo commentInfo) {
        JsonParser parser = new JsonParser();
        try (Reader reader = new FileReader(COMMENT_RECORD_PATH) ) {
            JsonElement je = parser.parse(reader);
            JsonObject jo = je.getAsJsonObject();
            if(!jo.has(name)) {
                return -1;
            } else {
                String timeStr = jo.get(name).getAsString();
                System.out.println("timeStr: " + timeStr);
                String[] parts = timeStr.split("-");
                LocalDate currentDate = LocalDate.now();
                int nowYear = currentDate.getYear();
                int nowMonth = currentDate.getMonthValue();
                int nowDay = currentDate.getDayOfMonth();

                int playerDays = (nowYear - Integer.parseInt(parts[0])) * 365
                        + (nowMonth - Integer.parseInt(parts[1])) * 30
                        + (nowDay - Integer.parseInt(parts[2]));

                int allMonth = playerDays / 30;
                int nextMonth =  0;
                int rewardLevel = 0;
                for (int i = 0; i < COMMENT_LEVEL.length; i++) {
                    if(allMonth < COMMENT_LEVEL[i]) {
                        nextMonth = COMMENT_LEVEL[i];
                        break;
                    }
                    rewardLevel++;
                }
                commentInfo.level = rewardLevel;
                commentInfo.markTime = timeStr;
                if(nextMonth!=0) {
                    commentInfo.nextLevelRemainDays = nextMonth * 30 - playerDays;
                } else {
                    commentInfo.nextLevelRemainDays = 0;
                }

                return rewardLevel;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}
