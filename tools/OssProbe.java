import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.CannedAccessControlList;

/**
 * 一次性诊断脚本：检查 OSS bucket 的读写权限与「阻止公共访问」开关，
 * 并尝试把 bucket 置为公共读（与产品需求一致：头像需要永久直链）。
 * <p>用法：java -cp "server/target/deps/*" tools/OssProbe.java</p>
 * <p>凭证从环境变量读取（与后端 OSS 配置同名）：
 * OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET，本机值见
 * backend/WitPath/server/src/main/resources/application-secret.yaml（不入库）。</p>
 */
public class OssProbe {

    public static void main(String[] args) {
        String endpoint = "oss-cn-shanghai.aliyuncs.com";
        String ak = System.getenv("OSS_ACCESS_KEY_ID");
        String sk = System.getenv("OSS_ACCESS_KEY_SECRET");
        String bucket = "witpath-avatar";

        if (ak == null || ak.isBlank() || sk == null || sk.isBlank()) {
            System.out.println("缺少环境变量 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET，请先设置（勿写死在源码中）");
            return;
        }

        OSS client = new OSSClientBuilder().build(endpoint, ak, sk);
        try {
            try {
                System.out.println("[bucket acl] " + client.getBucketAcl(bucket));
            } catch (Exception e) {
                System.out.println("[bucket acl] 读取失败: " + e.getMessage());
            }

            try {
                System.out.println("[bucket info] " + client.getBucketInfo(bucket).getBucket().getOwner());
            } catch (Exception e) {
                System.out.println("[bucket info] 读取失败: " + e.getMessage());
            }

            try {
                client.setBucketAcl(bucket, CannedAccessControlList.PublicRead);
                System.out.println("[set bucket acl] 已设为 public-read");
            } catch (Exception e) {
                System.out.println("[set bucket acl] 失败: " + e.getMessage());
            }

            try {
                System.out.println("[bucket acl after] " + client.getBucketAcl(bucket));
            } catch (Exception e) {
                System.out.println("[bucket acl after] 读取失败: " + e.getMessage());
            }
        } finally {
            client.shutdown();
        }
    }
}
