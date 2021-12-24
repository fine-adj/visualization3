package com.example.demo.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@Slf4j
public class HtmlController {

    @RequestMapping(value = "/")
    public String rootPath(){
        return "index";
    }

    @RequestMapping(value = "/chinamap", method = RequestMethod.GET)
    public String chinamap(){
        return "chinamap";
    }

    @RequestMapping(value = "/index")
    public String index(){
        return "index";
    }

    @RequestMapping(value = "/toponode")
    public String toponode(){
        return "toponode";
    }

    @RequestMapping(value = "/jstree")
    public String jstree(){
        return "jstree";
    }

    @RequestMapping(value = "/pathinfomap")
    public String pathinfomap(){
        return "pathinfomap";
    }

    @RequestMapping(value = "/moreCurves")
    public String moreCurves(){
        return "moreCurves";
    }

}
