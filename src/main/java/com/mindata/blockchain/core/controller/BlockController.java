package com.mindata.blockchain.core.controller;

import cn.hutool.core.collection.CollectionUtil;
import com.mindata.blockchain.ApplicationContextProvider;
import com.mindata.blockchain.block.Block;
import com.mindata.blockchain.block.Instruction;
import com.mindata.blockchain.block.Operation;
import com.mindata.blockchain.common.exception.TrustSDKException;
import com.mindata.blockchain.core.bean.BaseData;
import com.mindata.blockchain.core.bean.ResultGenerator;
import com.mindata.blockchain.core.event.DbSyncEvent;
import com.mindata.blockchain.core.manager.DbBlockManager;
import com.mindata.blockchain.core.manager.MessageManager;
import com.mindata.blockchain.core.manager.SyncManager;
import com.mindata.blockchain.core.requestbody.BlockRequestBody;
import com.mindata.blockchain.core.requestbody.InstructionBody;
import com.mindata.blockchain.core.service.BlockService;
import com.mindata.blockchain.core.service.InstructionService;
import com.mindata.blockchain.socket.body.RpcBlockBody;
import com.mindata.blockchain.socket.client.PacketSender;
import com.mindata.blockchain.socket.packet.BlockPacket;
import com.mindata.blockchain.socket.packet.PacketBuilder;
import com.mindata.blockchain.socket.packet.PacketType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author wuweifeng wrote on 2018/3/7.
 */
@RestController
@RequestMapping("/block")
public class BlockController {
    @Resource
    private BlockService blockService;
    @Resource
    private PacketSender packetSender;
    @Resource
    private DbBlockManager dbBlockManager;
    @Resource
    private InstructionService instructionService;
    @Resource
    private SyncManager syncManager;
    @Resource
    private MessageManager messageManager;

    /**
     * 添加一个block，需要先在InstructionController构建1-N个instruction指令，然后调用该接口生成Block
     *
     * @param blockRequestBody
     *         指令的集合
     * @return 结果
     */
    @PostMapping
    public BaseData add(@RequestBody BlockRequestBody blockRequestBody) throws TrustSDKException {
        if (blockService.check(blockRequestBody) != null) {
            return ResultGenerator.genFailResult(blockService.check(blockRequestBody));
        }
        return ResultGenerator.genSuccessResult(blockService.addBlock(blockRequestBody));
    }

    /**
     * 测试生成一个Block，公钥私钥可以通过PairKeyController来生成
     * @param content
     * sql内容
     */
    @GetMapping
    public BaseData test(String content) throws Exception {
        InstructionBody instructionBody = new InstructionBody();
        instructionBody.setOperation(Operation.ADD);
        instructionBody.setTable("message");
        instructionBody.setJson("{\"content\":\"" + content + "\"}");
        instructionBody.setPublicKey("A8WLqHTjcT/FQ2IWhIePNShUEcdCzu5dG+XrQU8OMu54");
        instructionBody.setPrivateKey("yScdp6fNgUU+cRUTygvJG4EBhDKmOMRrK4XJ9mKVQJ8=");
        Instruction instruction = instructionService.build(instructionBody);

        BlockRequestBody blockRequestBody = new BlockRequestBody();
        blockRequestBody.setPublicKey("A8WLqHTjcT/FQ2IWhIePNShUEcdCzu5dG+XrQU8OMu54");
        com.mindata.blockchain.block.BlockBody blockBody = new com.mindata.blockchain.block.BlockBody();
        blockBody.setInstructions(CollectionUtil.newArrayList(instruction));

       blockRequestBody.setBlockBody(blockBody);

        return ResultGenerator.genSuccessResult(blockService.addBlock(blockRequestBody));
    }

    /**
     * 查询已落地的sqlite里的所有数据
     */
    @GetMapping("sqlite")
    public BaseData sqlite() {
        return ResultGenerator.genSuccessResult(messageManager.findAll());
    }

    /**
     * 获取最后一个block的信息
     */
    @GetMapping("db")
    public BaseData getRockDB() {
        return ResultGenerator.genSuccessResult(dbBlockManager.getLastBlock());
    }

    /**
     * 手工执行区块内sql落地到sqlite操作
     * @param pageable
     * 分页
     * @return
     * 已同步到哪块了的信息
     */
    @GetMapping("sync")
    public BaseData sync(@PageableDefault Pageable pageable) {
        ApplicationContextProvider.publishEvent(new DbSyncEvent(""));
        return ResultGenerator.genSuccessResult(syncManager.findAll(pageable));
    }

    @GetMapping("/next")
    public BaseData nextBlock() {
        Block block = dbBlockManager.getFirstBlock();
        BlockPacket packet = new PacketBuilder<RpcBlockBody>()
                .setType(PacketType.NEXT_BLOCK_INFO_REQUEST)
                .setBody(new RpcBlockBody(block)).build();
        packetSender.sendGroup(packet);
        return null;
    }
}
