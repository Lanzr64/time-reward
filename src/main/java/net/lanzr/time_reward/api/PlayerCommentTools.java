package net.lanzr.time_reward.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.time.LocalDate;

public class PlayerCommentTools {
    static final String PATH = "lzFiles";
    static final String COMMENT_RECORD_PATH = PATH + "/comment-record.json";
    static final int[] COMMENT_LEVEL = {1,3,6,12,24};
    private static final Object FILE_LOCK = new Object();

    public static void init() {
        File file = new File(COMMENT_RECORD_PATH);

        // 确保父目录存在
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (parentDir.mkdirs()) {
                System.out.println("父目录创建成功！");
            } else {
                System.err.println("父目录创建失败！");
                return; // 无法创建父目录，直接退出
            }
        }

        if (!file.exists()) {
            try {
                if (file.createNewFile()) {
                    // 初始化文件为基础的json
                    JsonObject jsonObject = new JsonObject();
                    Gson gson = new Gson();
                    String jsonString = gson.toJson(jsonObject);
                    FileWriter fileWriter = new FileWriter(file);
                    fileWriter.write(jsonString);
                    fileWriter.close();

                    System.out.println("文件创建成功！");
                } else {
                    System.out.println("文件创建失败！"); // 可能是权限问题或磁盘空间不足
                }
            } catch (IOException e) {
                System.err.println("创建文件时发生错误: " + e.getMessage());
                e.printStackTrace(); // 打印更详细的错误信息
            }
        } else {
            System.out.println("文件已存在！");
        }
    }
    public static int getPlayerComment(String name, CommentInfo commentInfo) {
        JsonParser parser = new JsonParser();
        synchronized (FILE_LOCK) {
            try (Reader reader = new FileReader(COMMENT_RECORD_PATH) ) {
                JsonElement je = parser.parse(reader);
                JsonObject jo = je.getAsJsonObject();
                if(!jo.has(name)) {
                    System.out.println("no record");
                    return -1;
                } else {
                    System.out.println("have record");
                    // 存在记录但是没有领取
                    String timeStr = jo.get(name).getAsString();
                    getPlayerCommentInfo(timeStr, commentInfo);
                    return 0;
                }

            } catch (Exception e) {
                e.printStackTrace();
                return -1;
            }
        }
    }

    public static int addPlayerRecord(String name, int year, int month, int day) {
        synchronized (FILE_LOCK) {
            try (Reader reader = new FileReader(COMMENT_RECORD_PATH)) {
                JsonParser parser = new JsonParser();
                JsonElement je = parser.parse(reader);
                JsonObject jo = je.getAsJsonObject();
                jo.addProperty(name, year + "-" + month + "-" + day);
                PrintStream out = new PrintStream(new File(COMMENT_RECORD_PATH));
                out.print(jo.toString());
            } catch (Exception e) {
                e.printStackTrace();
                return -1;
            }
        }
        return 0;
    }

    private static int getPlayerCommentInfo(String timeStr, CommentInfo commentInfo) {
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
        return 0;
    }
}
