package com.github.rvesse.baby.photo.sorter;

import java.io.IOException;

import com.github.rvesse.airline.SingleCommand;
import com.github.rvesse.airline.help.Help;
import com.github.rvesse.airline.parser.ParseResult;
import com.github.rvesse.airline.parser.errors.ParseException;

public class Launcher {

    public static void main(String[] args) {
      SingleCommand<BabyPhotoSorter> parser = SingleCommand.singleCommand(BabyPhotoSorter.class);
      
      ParseResult<BabyPhotoSorter> result = parser.parseWithResult(args);
      if (result.wasSuccessful()) {
          result.getCommand().run();
      } else {
          for (ParseException e : result.getErrors()) {
              System.err.println(e.getMessage());
          }
          System.err.println();
          
          try {
            Help.help(parser.getCommandMetadata());
        } catch (IOException e) {
            e.printStackTrace();
        }
      }
    }
}
