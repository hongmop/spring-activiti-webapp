package org.activiti.web.simple.webapp.controller;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.IdentityService;
import org.activiti.engine.ManagementService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping(value="/workflow")
public class WorkFlowController {
	
	@Resource(name="identityService")
	private IdentityService identityService;
	
	@Resource(name="runtimeService")
	private RuntimeService runtimeService;
	
	@Resource(name="historyService")
	private HistoryService historyService;
	
	@Resource(name="taskService")
	private TaskService taskService;
	
	@Resource(name="managementService")
	private ManagementService managementService;
	
	@Resource(name="formService")
	private FormService formService;
	
	@Resource(name="repositoryService")
	private RepositoryService repositoryService;
	
	
	@RequestMapping("/toupload")
	public String toupload(){
		return "workflow/upload";
	}
	
	/**
	 * 部署流程定义文件(Spring MVC文件上传)
	 * @return
	 * @throws Exception 
	 */
	@RequestMapping(value="/deploy",method=RequestMethod.POST)
	public String deploy(@RequestParam("username")String username,@RequestParam("file")MultipartFile file,HttpServletRequest request, HttpServletResponse response,RedirectAttributes redirectAttributes) throws Exception{
		System.out.println(username);
		if(!file.isEmpty()){
			//获取文件字节数组
			byte[] bytes= file.getBytes();
			//获取文件保存路径
			String realPath = request.getSession().getServletContext().getRealPath("/upload");
			File out=new File(realPath,file.getOriginalFilename());
			//将文件写到指定目录下
			FileUtils.writeByteArrayToFile(out, bytes);
			
			if(FilenameUtils.getExtension(file.getOriginalFilename()).equals("zip")||FilenameUtils.getExtension(file.getOriginalFilename()).equals("bar")){
				
				ZipInputStream zipInputStream=new ZipInputStream(file.getInputStream());
				//部署流程定义文件
				repositoryService.createDeployment().addZipInputStream(zipInputStream).deploy();
				
			}else{
				redirectAttributes.addFlashAttribute("message", "请上传zip或bar格式的文件!");
				return "redirect:/workflow/toupload";
			}
			redirectAttributes.addFlashAttribute("message", "文件上传成功!保存在:"+out.getPath());
		}
		return "redirect:/workflow/processlist";
	}
	
	
	@RequestMapping(value="/processlist",method={RequestMethod.POST,RequestMethod.GET})
	public ModelAndView processlist(HttpServletRequest request, HttpServletResponse response){
		
		ModelAndView modelAndView=new ModelAndView("workflow/processlist");
		
		/*
		 * 保存两个对象，一个是ProcessDefinition（流程定义），一个是Deployment（流程部署）
		 */
		List<Object[]> objects = new ArrayList<Object[]>();
		
		List<ProcessDefinition> list = repositoryService.createProcessDefinitionQuery().list();
		for (ProcessDefinition processDefinition : list) {
			String deploymentId = processDefinition.getDeploymentId();
			Deployment deployment = repositoryService.createDeploymentQuery().deploymentId(deploymentId).singleResult();
			objects.add(new Object[]{processDefinition,deployment});
		}
		modelAndView.addObject("objects",objects);
		return modelAndView;
	}
	
	
	/**
	 * 根据流程部署Id和资源名称加载流程资源
	 * @param deploymentId
	 * @param resourceName
	 * @param request
	 * @param response
	 */
	@RequestMapping(value="/loadResourceByDeployment",method={RequestMethod.GET,RequestMethod.POST})
	public void loadResourceByDeployment(@RequestParam("deploymentId")String deploymentId,@RequestParam("resourceName")String resourceName,HttpServletRequest request, HttpServletResponse response){
		
		InputStream resourceAsStream = repositoryService.getResourceAsStream(deploymentId, resourceName);
		try {
			byte[] byteArray = IOUtils.toByteArray(resourceAsStream);
			ServletOutputStream servletOutputStream = response.getOutputStream();
			servletOutputStream.write(byteArray, 0, byteArray.length);
			servletOutputStream.flush();
			servletOutputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 根据流程部署Id级联删除已部署的流程
	 * @param deploymentId
	 * @param request
	 * @param response
	 * @return 跳转到已部署的流程列表
	 */
	@RequestMapping(value="/deleteDeploymentById/{deploymentId}",method={RequestMethod.GET})
	public String deleteDeploymentById(@PathVariable("deploymentId")String deploymentId,HttpServletRequest request, HttpServletResponse response,RedirectAttributes redirectAttributes){
		try {
			repositoryService.deleteDeployment(deploymentId,true);
			redirectAttributes.addFlashAttribute("message", "已部署的流程"+deploymentId+"已成功删除!");
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("message", "已部署的流程"+deploymentId+"删除失败!");
		}
		return "redirect:/workflow/processlist";
	}
	
	
	/**
	 * 根据流程实例Id和资源类型加载流程资源
	 * @param processInstanceId 流程实例id
	 * @param resourceType 流程资源类型
	 * @param request
	 * @param response
	 */
	@RequestMapping(value="/loadResourceByProcessInstance",method={RequestMethod.GET,RequestMethod.POST})
	public void loadResourceByProcessInstance(@RequestParam("processInstanceId")String processInstanceId,@RequestParam("resourceType")String resourceType,HttpServletRequest request, HttpServletResponse response){
		//根据流程实例id查询流程实例
		ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
		//根据流程定义id查询流程定义
		ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(processInstance.getProcessDefinitionId()).singleResult();
		
		String resourceName="";
		if(resourceType.equals("xml")){
			//获取流程定义资源名称
			resourceName=processDefinition.getResourceName();
		}else if(resourceType.equals("image")){
			//获取流程图资源名称
			resourceName=processDefinition.getDiagramResourceName();
		}
		//打开流程资源流
		InputStream resourceAsStream = repositoryService.getResourceAsStream(processDefinition.getDeploymentId(), resourceName);
		//输出到浏览器
		try {
			byte[] byteArray = IOUtils.toByteArray(resourceAsStream);
			ServletOutputStream servletOutputStream = response.getOutputStream();
			servletOutputStream.write(byteArray, 0, byteArray.length);
			servletOutputStream.flush();
			servletOutputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	
	/**
	 * 根据任务id查询流程图(跟踪流程图)
	 * @param taskId 任务id
	 * @param request
	 * @param response
	 */
	@RequestMapping(value="/view/{taskId}",method={RequestMethod.GET,RequestMethod.POST})
	public void viewProcessImageView(@PathVariable("taskId")String taskId,HttpServletRequest request, HttpServletResponse response){
		InputStream resourceAsStream = null;
		try {
			
			
			
			
			byte[] byteArray = IOUtils.toByteArray(resourceAsStream);
			ServletOutputStream servletOutputStream = response.getOutputStream();
			servletOutputStream.write(byteArray, 0, byteArray.length);
			servletOutputStream.flush();
			servletOutputStream.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
}
