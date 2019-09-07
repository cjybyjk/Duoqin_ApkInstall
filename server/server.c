// Author: cjybyjk @ coolapk

#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/select.h>

#define LISTEN_IP "127.0.0.1"         //只监听本地回环地址，防止未授权的连接
#define SERV_PORT 1226                //监听端口，1226是德井青空的生日~
#define LIST 8                        //服务器最大接受连接
#define MAX_FD 10                     //FD_SET支持描述符数量
#define APK_INSTALLER_PATH "/data/local/tmp/apkinstall.sh"

#define MSG_WELCOME                         "Welcome to PackageInstallServer!\n"
#define MSG_CLIENT_CONNECTED                "Client connected: ip: %s , port %d\n"
#define MSG_CLIENT_JOIN                     "Client %d joined\n"
#define MSG_CLIENT_DISCONNECTED             "Client %d disconnected\n"
#define MSG_RECEIVED                        "Received from client %d: %s\n"

#define ERR_TIMEOUT                         "TimeOut!\n"
#define ERR_FILE_NOT_FOUND                  "File not found!\n"
#define ERR_SOCKET_CREATE_FAILED            "Failed to create socket!\n"
#define ERR_SOCKET_BIND_FAILED              "Failed to bind socket!\n"
#define ERR_SOCKET_LISTEN_FAILED            "Failed to listen from socket!\n"
#define ERR_CONN_ACCEPT_FAILED              "Failed to accept connection!\n"
#define ERR_CONN_LIST_SELECT_FAILED         "Failed to select from connection list!\n"
#define ERR_CLIENT_WRITE_FAILED             "Failed to write data to client %d!\n"

void pInstallPackage(char *path, int retSocket) {
    if((access(path,R_OK)) != -1)
    {
        FILE *pf;
        int num;
        char buf_ps[1024];                       //缓冲区
        char cmd[1024] = APK_INSTALLER_PATH;     //拼接命令
        strcat(cmd, " '");
        strcat(cmd, path);
        strcat(cmd, "' 2>&1");
        if((pf=popen(cmd, "r")) != NULL)   
        {
            while(fgets(buf_ps, 1024, pf)!=NULL)   
            {
                num = write(retSocket,buf_ps,strlen(buf_ps));
                if(num < 0){
                    printf(ERR_CLIENT_WRITE_FAILED, retSocket);
                    break;
                }
            }
            pclose(pf);
            pf = NULL;
        } 
    } else {
        write(retSocket, ERR_FILE_NOT_FOUND, strlen(ERR_FILE_NOT_FOUND));
    }
    
}

int main(int argc, char *argv[])
{
    int sockfd;
    int err;
    int i;
    int connfd;
    int fd_all[MAX_FD]; //保存所有描述符，用于select调用后，判断哪个可读

    //下面两个备份原因是select调用后，会发生变化，再次调用select前，需要重新赋值
    fd_set fd_read;    //FD_SET数据备份
    fd_set fd_select;  //用于select

    struct timeval timeout;         //超时时间备份
    struct timeval timeout_select;  //用于select

    struct sockaddr_in serv_addr;   //服务器地址
    struct sockaddr_in cli_addr;    //客户端地址
    socklen_t serv_len;
    socklen_t cli_len;

    //超时时间设置
    timeout.tv_sec = 10;
    timeout.tv_usec = 0;

    //创建TCP套接字
    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if(sockfd < 0)
    {
        perror(ERR_SOCKET_CREATE_FAILED);
        exit(1);
    }

    // 配置本地地址
    memset(&serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;         // ipv4
    serv_addr.sin_port = htons(SERV_PORT);  // 端口
    serv_addr.sin_addr.s_addr = inet_addr(LISTEN_IP); // 侦听ip

    serv_len = sizeof(serv_addr);

    // 绑定
    err = bind(sockfd, (struct sockaddr *)&serv_addr, serv_len);
    if(err < 0)
    {
        perror(ERR_SOCKET_BIND_FAILED);
        exit(1);
    }

    // 监听
    err = listen(sockfd, LIST);
    if(err < 0)
    {
        perror(ERR_SOCKET_LISTEN_FAILED);
        exit(1);
    }

    //初始化fd_all数组
    memset(fd_all, -1, sizeof(fd_all));

    fd_all[0] = sockfd;   //第一个为监听套接字

    FD_ZERO(&fd_read);  // 清空
    FD_SET(sockfd, &fd_read);  //将监听套接字加入fd_read

    int maxfd = fd_all[0];  //监听的最大套接字

    while(1){

        // 每次都需要重新赋值，fd_select，timeout_select每次都会变
        fd_select = fd_read;
        timeout_select = timeout;

        // 检测监听套接字是否可读，没有可读，此函数会阻塞
        // 只要有客户连接，或断开连接，select()都会往下执行
        err = select(maxfd+1, &fd_select, NULL, NULL, NULL);
        if(err < 0)
        {
                perror(ERR_CONN_LIST_SELECT_FAILED);
                exit(1);
        }

        if(err == 0){
            printf(ERR_TIMEOUT);
        }

        // 检测监听套接字是否可读
        if(FD_ISSET(sockfd, &fd_select)){ //可读，证明有新客户端连接服务器

            cli_len = sizeof(cli_addr);

            // 取出已经完成的连接
            connfd = accept(sockfd, (struct sockaddr *)&cli_addr, &cli_len);
            if(connfd < 0)
            {
                perror(ERR_CONN_ACCEPT_FAILED);
                exit(1);
            }

            // 打印客户端的 ip 和端口
            char cli_ip[INET_ADDRSTRLEN] = {0};
            inet_ntop(AF_INET, &cli_addr.sin_addr, cli_ip, INET_ADDRSTRLEN);
            printf("----------------------------------------------\n");
            printf(MSG_CLIENT_CONNECTED, cli_ip, ntohs(cli_addr.sin_port));
            // 发出欢迎信息
            write(connfd, MSG_WELCOME, sizeof(MSG_WELCOME));

            // 将新连接套接字加入 fd_all 及 fd_read
            for(i=0; i < MAX_FD; i++){
                if(fd_all[i] != -1){
                    continue;
                }else{
                    fd_all[i] = connfd;
                    printf(MSG_CLIENT_JOIN, i);
                    break;
                }
            }

            FD_SET(connfd, &fd_read);
            if(maxfd < connfd)
            {
                maxfd = connfd;  //更新maxfd
            }
        }

        //从1开始查看连接套接字是否可读，因为上面已经处理过0（sockfd）
        for(i=1; i < maxfd; i++){
            if(FD_ISSET(fd_all[i], &fd_select)){
                // printf("fd_all[%d] is ok\n", i);
                char buf[1024]={0};   //读写缓冲区
                memset(buf, 0 , sizeof(buf));
                int num = read(fd_all[i], buf, sizeof(buf));
                if(num > 0){
                    //收到 客户端数据并打印
                    printf(MSG_RECEIVED, i, buf);
                    // 执行安装过程
                    pInstallPackage(buf, fd_all[i]);
                }
                else if(0 == num){ // 客户端断开时
                    //客户端退出，关闭套接字，并从监听集合清除
                    printf(MSG_CLIENT_DISCONNECTED, i);
                    FD_CLR(fd_all[i], &fd_read);
                    close(fd_all[i]);
                    fd_all[i] = -1;
                    continue;
                }
            }
        }
    }

    return 0;
}
