package com.seckill.controller;

import com.seckill.common.Constants;
import com.seckill.common.Result;
import com.seckill.entity.SeckillActivity;
import com.seckill.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    @GetMapping("/list")
    public Result<List<SeckillActivity>> list() {
        return Result.success(seckillService.listActive());
    }

    @GetMapping("/listAll")
    public Result<List<SeckillActivity>> listAll() {
        return Result.success(seckillService.listAll());
    }

    @PutMapping("/{id}/status")
    public Result<?> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        seckillService.updateStatus(id, status);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<SeckillActivity> getById(@PathVariable Long id) {
        return Result.success(seckillService.getById(id));
    }

    @PostMapping
    public Result<SeckillActivity> create(@Valid @RequestBody SeckillActivity activity) {
        return Result.success(seckillService.create(activity));
    }

    @PostMapping("/do/{activityId}")
    public Result<?> doSeckill(@PathVariable Long activityId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(Constants.USER_ID);
        try {
            boolean success = seckillService.seckill(userId, activityId);
            if (success) {
                return Result.success("秒杀成功");
            }
            return Result.error("秒杀失败");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/check/{activityId}")
    public Result<Boolean> checkBought(@PathVariable Long activityId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(Constants.USER_ID);
        return Result.success(seckillService.checkBought(userId, activityId));
    }
}
