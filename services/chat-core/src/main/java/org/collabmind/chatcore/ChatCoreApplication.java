package org.collabmind.chatcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChatCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatCoreApplication.class, args);
    }
}
