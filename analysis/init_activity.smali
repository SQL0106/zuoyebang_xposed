# file_id=0 method_idx=42454 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;-><init>()V"
.method public constructor <init>()V
    .registers 4

    invoke-direct {v3}, Landroidx/fragment/app/FragmentActivity;-><init>()V
    const/4 v0, -0x1
    iput v0, v3, Lcom/baidu/homework/activity/init/InitActivity;->h0:I
    iput v0, v3, Lcom/baidu/homework/activity/init/InitActivity;->i0:I
    const/4 v0, 0x0
    iput-boolean v0, v3, Lcom/baidu/homework/activity/init/InitActivity;->m0:Z
    iput-boolean v0, v3, Lcom/baidu/homework/activity/init/InitActivity;->n0:Z
    new-instance v1, Lcom/baidu/homework/activity/init/InitActivity$a;
    invoke-direct {v1, v3}, Lcom/baidu/homework/activity/init/InitActivity$a;-><init>(Lcom/baidu/homework/activity/init/InitActivity;)V
    iput-object v1, v3, Lcom/baidu/homework/activity/init/InitActivity;->o0:Lcom/baidu/homework/activity/init/InitActivity$a;
    const/4 v1, 0x0
    iput-object v1, v3, Lcom/baidu/homework/activity/init/InitActivity;->p0:Lcom/homework/abtest/i;
    const-wide/16 v1, 0x0
    iput-wide v1, v3, Lcom/baidu/homework/activity/init/InitActivity;->q0:J
    iput-boolean v0, v3, Lcom/baidu/homework/activity/init/InitActivity;->r0:Z
    iput-boolean v0, v3, Lcom/baidu/homework/activity/init/InitActivity;->s0:Z
    iput-boolean v0, v3, Lcom/baidu/homework/activity/init/InitActivity;->t0:Z
    new-instance v0, Lcom/baidu/homework/activity/init/b;
    invoke-direct {v0, v3}, Lcom/baidu/homework/activity/init/b;-><init>(Lcom/baidu/homework/activity/init/InitActivity;)V
    iput-object v0, v3, Lcom/baidu/homework/activity/init/InitActivity;->v0:Ljava/lang/Runnable;
    return-void
.end method

# file_id=0 method_idx=42455 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->A0()V"
.method private A0()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1210
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0016
    return-void
    :label_0016
    invoke-static {}, Lcom/baidu/homework/ads/b;->h()Z
    move-result v0
    if-eqz v0, :label_0023
    const/4 v0, 0x1
    iput-boolean v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->m0:Z
    invoke-direct {v8}, Lcom/baidu/homework/activity/init/InitActivity;->O0()V
    goto :label_0026
    :label_0023
    invoke-direct {v8}, Lcom/baidu/homework/activity/init/InitActivity;->C0()V
    :label_0026
    return-void
.end method

# file_id=0 method_idx=42456 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->B0()V"
.method private B0()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1212
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0016
    return-void
    :label_0016
    invoke-static {}, Lcom/baidu/homework/common/utils/f0;->e()Z
    move-result v0
    if-eqz v0, :label_0021
    sget-object v0, Lcom/baidu/homework/activity/init/e;->a:Lcom/baidu/homework/activity/init/e;
    invoke-static {v0}, Lcom/baidu/homework/common/utils/s1;->k(Lcom/baidu/homework/common/utils/s1$a;)V
    :label_0021
    return-void
.end method

# file_id=0 method_idx=42457 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->C0()V"
.method private C0()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1211
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v1
    iget-boolean v1, v1, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_0016
    return-void
    :label_0016
    iput-boolean v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->m0:Z
    const-string v0, "START_INIT_WITHOUT_ADX_MODULE"
    invoke-static {v0}, Lcom/baidu/homework/common/p/g;->r(Ljava/lang/String;)V
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v0
    iput-wide v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->q0:J
    new-instance v0, Lcom/baidu/homework/activity/init/c;
    invoke-direct {v0, v8}, Lcom/baidu/homework/activity/init/c;-><init>(Lcom/baidu/homework/activity/init/InitActivity;)V
    invoke-direct {v8, v0}, Lcom/baidu/homework/activity/init/InitActivity;->v0(Lcom/baidu/homework/base/e0;)V
    return-void
.end method

# file_id=0 method_idx=42458 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->D0()Z"
.method private D0()Z
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Boolean;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1213
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v1
    iget-boolean v2, v1, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v2, :label_001e
    iget-object v0, v1, Lcom/meituan/robust/PatchProxyResult;->result:Ljava/lang/Object;
    check-cast v0, Ljava/lang/Boolean;
    invoke-virtual {v0}, Ljava/lang/Boolean;->booleanValue()Z
    move-result v0
    return v0
    :label_001e
    sget-object v1, Lcom/baidu/homework/common/CommonPreference;->KEY_INSTALL_STAT:Lcom/baidu/homework/common/CommonPreference;
    invoke-static {v1}, Lcom/baidu/homework/common/utils/e1;->f(Ljava/lang/Enum;)I
    move-result v1
    if-nez v1, :label_0027
    const/4 v0, 0x1
    :label_0027
    return v0
.end method

# file_id=0 method_idx=42459 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->E0(Lcom/baidu/homework/base/e0;Lcom/homework/abtest/j;)V"
.method static synthetic E0(Lcom/baidu/homework/base/e0;Lcom/homework/abtest/j;)V
    .registers 11

    const/4 v0, 0x2
    new-array v1, v0, [Ljava/lang/Object;
    const/4 v2, 0x0
    aput-object v9, v1, v2
    const/4 v8, 0x1
    aput-object v10, v1, v8
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    const-class v0, Lcom/baidu/homework/base/e0;
    aput-object v0, v6, v2
    const-class v0, Lcom/homework/abtest/j;
    aput-object v0, v6, v8
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v2, 0x0
    const/4 v4, 0x1
    const/16 v5, 0x1217
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0024
    return-void
    :label_0024
    sget-object v0, Lcom/homework/abtest/j;->OVERTIME:Lcom/homework/abtest/j;
    if-ne v10, v0, :label_0035
    const-string v10, "experimentStat"
    const-string v0, "timeout"
    filled-new-array {v10, v0}, [Ljava/lang/String;
    move-result-object v10
    const-string v0, "JC_N1_14_1"
    invoke-static {v0, v10}, Lcom/baidu/homework/common/p/g;->v(Ljava/lang/String;[Ljava/lang/String;)V
    :label_0035
    sget-object v10, Lcom/zybang/lib/LibPreference;->GLIDE_SUPPORT_APNG:Lcom/zybang/lib/LibPreference;
    invoke-static {v10, v8}, Lcom/baidu/homework/common/utils/e1;->t(Ljava/lang/Enum;Z)V
    if-eqz v9, :label_003f
    invoke-interface {v9}, Lcom/baidu/homework/base/e0;->call()V
    :label_003f
    return-void
.end method

# file_id=0 method_idx=42460 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->F0(Landroid/content/Context;IF)I"
.method static synthetic F0(Landroid/content/Context;IF)I
    .registers 11

    const/4 v0, 0x3
    new-array v1, v0, [Ljava/lang/Object;
    const/4 v2, 0x0
    aput-object v8, v1, v2
    new-instance v3, Ljava/lang/Integer;
    invoke-direct {v3, v9}, Ljava/lang/Integer;-><init>(I)V
    const/4 v4, 0x1
    aput-object v3, v1, v4
    new-instance v3, Ljava/lang/Float;
    invoke-direct {v3, v10}, Ljava/lang/Float;-><init>(F)V
    const/4 v5, 0x2
    aput-object v3, v1, v5
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    const-class v0, Landroid/content/Context;
    aput-object v0, v6, v2
    sget-object v7, Ljava/lang/Integer;->TYPE:Ljava/lang/Class;
    aput-object v7, v6, v4
    sget-object v0, Ljava/lang/Float;->TYPE:Ljava/lang/Class;
    aput-object v0, v6, v5
    const/4 v2, 0x0
    const/16 v5, 0x1214
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v1, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_003a
    iget-object v8, v0, Lcom/meituan/robust/PatchProxyResult;->result:Ljava/lang/Object;
    check-cast v8, Ljava/lang/Integer;
    invoke-virtual {v8}, Ljava/lang/Integer;->intValue()I
    move-result v8
    return v8
    :label_003a
    if-gtz v9, :label_0051
    const/16 v9, 0x19
    const/high16 v0, -0x40800000
    cmpl-float v0, v10, v0
    if-nez v0, :label_004a
    int-to-float v9, v9
    invoke-static {v8, v9}, Lcom/baidu/homework/common/q/a/a;->b(Landroid/content/Context;F)I
    move-result v9
    goto :label_0051
    :label_004a
    int-to-float v8, v9
    mul-float v8, v8, v10
    const/high16 v9, 0x3f000000
    add-float/2addr v8, v9
    float-to-int v9, v8
    :label_0051
    return v9
.end method

# file_id=0 method_idx=42461 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->G0()V"
.method private synthetic G0()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1215
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0016
    return-void
    :label_0016
    iget-object v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->v0:Ljava/lang/Runnable;
    invoke-interface {v0}, Ljava/lang/Runnable;->run()V
    return-void
.end method

# file_id=0 method_idx=42462 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->H0()V"
.method public synthetic H0()V
    .registers 1

    invoke-direct {v0}, Lcom/baidu/homework/activity/init/InitActivity;->G0()V
    return-void
.end method

# file_id=0 method_idx=42463 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->I0()V"
.method private synthetic I0()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1218
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0016
    return-void
    :label_0016
    const/4 v0, 0x1
    iput-boolean v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->r0:Z
    iget-boolean v1, v8, Lcom/baidu/homework/activity/init/InitActivity;->s0:Z
    if-eqz v1, :label_0032
    iget-object v1, v8, Lcom/baidu/homework/activity/init/InitActivity;->v0:Ljava/lang/Runnable;
    if-eqz v1, :label_0032
    iget-boolean v1, v8, Lcom/baidu/homework/activity/init/InitActivity;->t0:Z
    if-nez v1, :label_0032
    iput-boolean v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->t0:Z
    iget-object v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->u0:Lcom/baidu/homework/ads/a;
    const/4 v1, 0x0
    invoke-virtual {v0, v1}, Landroid/os/Handler;->removeCallbacksAndMessages(Ljava/lang/Object;)V
    iget-object v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->v0:Ljava/lang/Runnable;
    invoke-interface {v0}, Ljava/lang/Runnable;->run()V
    :label_0032
    return-void
.end method

# file_id=0 method_idx=42464 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->J0()V"
.method public synthetic J0()V
    .registers 1

    invoke-direct {v0}, Lcom/baidu/homework/activity/init/InitActivity;->I0()V
    return-void
.end method

# file_id=0 method_idx=42465 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->K0()V"
.method private synthetic K0()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1216
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v1
    iget-boolean v1, v1, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_0016
    return-void
    :label_0016
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v1
    iget-wide v3, v8, Lcom/baidu/homework/activity/init/InitActivity;->q0:J
    sub-long/2addr v1, v3
    sput-wide v1, Lcom/baidu/homework/common/g;->s:J
    sget-object v1, Lcom/baidu/homework/activity/index/IndexPreference;->FORCE_LOGIN_SHOW_COUNT:Lcom/baidu/homework/activity/index/IndexPreference;
    invoke-static {v1}, Lcom/baidu/homework/common/utils/e1;->f(Ljava/lang/Enum;)I
    move-result v1
    sget-object v2, Lcom/baidu/homework/activity/index/IndexPreference;->FORCE_LOGIN_LAST_SHOW_TIME:Lcom/baidu/homework/activity/index/IndexPreference;
    invoke-static {v2}, Lcom/baidu/homework/common/utils/e1;->h(Ljava/lang/Enum;)Ljava/lang/Long;
    move-result-object v2
    invoke-virtual {v2}, Ljava/lang/Long;->longValue()J
    move-result-wide v2
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v4
    invoke-static {v2, v3, v4, v5}, Lcom/baidu/homework/common/utils/a0;->g(JJ)Z
    move-result v2
    if-nez v2, :label_003b
    if-lez v1, :label_003c
    :label_003b
    const/4 v0, 0x1
    :label_003c
    invoke-static {}, Lcom/baidu/homework/common/login/e;->l()Lcom/baidu/homework/common/login/e;
    move-result-object v1
    invoke-virtual {v1}, Lcom/baidu/homework/common/login/e;->w()Z
    move-result v1
    if-nez v1, :label_005b
    if-eqz v0, :label_0049
    goto :label_005b
    :label_0049
    invoke-static {}, Lcom/baidu/homework/common/login/e;->l()Lcom/baidu/homework/common/login/e;
    move-result-object v0
    const/16 v1, 0x3e9
    invoke-virtual {v0, v8, v1}, Lcom/baidu/homework/common/login/e;->y(Landroid/app/Activity;I)V
    :label_0052
    goto :label_005e
    :label_0053
    move-exception v0
    invoke-virtual {v0}, Ljava/lang/Exception;->printStackTrace()V
    invoke-direct {v8}, Lcom/baidu/homework/activity/init/InitActivity;->y0()V
    goto :label_005e
    :label_005b
    invoke-direct {v8}, Lcom/baidu/homework/activity/init/InitActivity;->y0()V
    :label_005e
    const/4 v0, 0x3
    invoke-static {v0}, Lcom/baidu/homework/common/o/b;->l(I)V
    sget-object v0, Lcom/baidu/homework/common/utils/s;->a:Lcom/baidu/homework/common/utils/s;
    invoke-virtual {v0, v8}, Lcom/baidu/homework/common/utils/s;->a(Landroid/content/Context;)V
    invoke-direct {v8}, Lcom/baidu/homework/activity/init/InitActivity;->D0()Z
    move-result v0
    if-eqz v0, :label_0077
    invoke-direct {v8}, Lcom/baidu/homework/activity/init/InitActivity;->B0()V
    invoke-static {}, Lcom/baidu/homework/base/h0;->d()Landroid/app/Application;
    move-result-object v0
    invoke-static {v0}, Lcom/baidu/homework/common/utils/s1;->f(Landroid/content/Context;)I
    :label_0077
    return-void
    .catch Ljava/lang/Exception; {:label_0049 .. :label_0052} :label_0053
.end method

# file_id=0 method_idx=42466 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->L0()V"
.method public synthetic L0()V
    .registers 1

    invoke-direct {v0}, Lcom/baidu/homework/activity/init/InitActivity;->K0()V
    return-void
.end method

# file_id=0 method_idx=42467 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->M0()V"
.method private synthetic M0()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1219
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0016
    return-void
    :label_0016
    invoke-direct {v8}, Lcom/baidu/homework/activity/init/InitActivity;->init()V
    return-void
.end method

# file_id=0 method_idx=42468 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->N0()V"
.method public synthetic N0()V
    .registers 1

    invoke-direct {v0}, Lcom/baidu/homework/activity/init/InitActivity;->M0()V
    return-void
.end method

# file_id=0 method_idx=42469 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->O0()V"
.method private O0()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x11fd
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v1
    iget-boolean v1, v1, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_0016
    return-void
    :label_0016
    iput-boolean v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->t0:Z
    iget-object v1, v8, Lcom/baidu/homework/activity/init/InitActivity;->u0:Lcom/baidu/homework/ads/a;
    if-nez v1, :label_002c
    new-instance v1, Lcom/baidu/homework/ads/a;
    invoke-static {}, Landroid/os/Looper;->myLooper()Landroid/os/Looper;
    move-result-object v2
    new-instance v3, Ljava/lang/ref/WeakReference;
    invoke-direct {v3, v8}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V
    invoke-direct {v1, v2, v3}, Lcom/baidu/homework/ads/a;-><init>(Landroid/os/Looper;Ljava/lang/ref/WeakReference;)V
    iput-object v1, v8, Lcom/baidu/homework/activity/init/InitActivity;->u0:Lcom/baidu/homework/ads/a;
    :label_002c
    iget-object v1, v8, Lcom/baidu/homework/activity/init/InitActivity;->u0:Lcom/baidu/homework/ads/a;
    invoke-static {}, Lcom/baidu/homework/ads/b;->f()J
    move-result-wide v2
    invoke-virtual {v1, v0, v2, v3}, Landroid/os/Handler;->sendEmptyMessageDelayed(IJ)Z
    const/4 v0, 0x0
    const v1, 0x7f091342
    :label_0039
    invoke-virtual {v8, v1}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
    move-result-object v1
    check-cast v1, Landroid/widget/FrameLayout;
    :label_003f
    move-object v4, v1
    goto :label_0046
    :label_0041
    move-exception v1
    invoke-virtual {v1}, Ljava/lang/Exception;->printStackTrace()V
    move-object v4, v0
    :label_0046
    if-eqz v4, :label_005f
    invoke-static {v8}, Lcom/baidu/homework/ads/b;->e(Landroid/content/Context;)Lcom/baidu/homework/ads/advertise/b;
    move-result-object v5
    invoke-virtual {v8}, Landroidx/activity/ComponentActivity;->getLifecycle()Landroidx/lifecycle/Lifecycle;
    move-result-object v3
    new-instance v6, Lcom/baidu/homework/activity/init/InitActivity$c;
    invoke-direct {v6, v8}, Lcom/baidu/homework/activity/init/InitActivity$c;-><init>(Lcom/baidu/homework/activity/init/InitActivity;)V
    new-instance v7, Lcom/baidu/homework/activity/init/InitActivity$b;
    invoke-direct {v7, v8}, Lcom/baidu/homework/activity/init/InitActivity$b;-><init>(Lcom/baidu/homework/activity/init/InitActivity;)V
    move-object v2, v8
    invoke-static/range {v2 .. v7}, Lcom/baidu/homework/ads/advertise/o;->g(Landroid/app/Activity;Landroidx/lifecycle/Lifecycle;Landroid/view/ViewGroup;Lcom/baidu/homework/ads/advertise/b;Lcom/baidu/homework/ads/advertise/g;Lcom/baidu/homework/ads/advertise/d;)V
    goto :label_0062
    :label_005f
    invoke-virtual {v8}, Lcom/baidu/homework/activity/init/InitActivity;->u0()V
    :label_0062
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v0
    iput-wide v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->q0:J
    new-instance v0, Lcom/baidu/homework/activity/init/d;
    invoke-direct {v0, v8}, Lcom/baidu/homework/activity/init/d;-><init>(Lcom/baidu/homework/activity/init/InitActivity;)V
    invoke-direct {v8, v0}, Lcom/baidu/homework/activity/init/InitActivity;->v0(Lcom/baidu/homework/base/e0;)V
    const-string v0, "LOAD_START_SPLASH_AD"
    invoke-static {v0}, Lcom/baidu/homework/common/p/g;->r(Ljava/lang/String;)V
    return-void
    .catch Ljava/lang/Exception; {:label_0039 .. :label_003f} :label_0041
.end method

# file_id=0 method_idx=42470 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->P0()V"
.method private P0()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x120f
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0016
    return-void
    :label_0016
    invoke-static {}, Lcom/zuoyebang/rlog/logger/c;->j()Z
    move-result v0
    if-eqz v0, :label_0031
    new-instance v0, Lcom/zuoyebang/rlog/logger/AppDotEvent;
    const-string v1, "BasePerf_UsageInit"
    invoke-direct {v0, v1}, Lcom/zuoyebang/rlog/logger/AppDotEvent;-><init>(Ljava/lang/String;)V
    invoke-virtual {v8}, Ljava/lang/Object;->getClass()Ljava/lang/Class;
    move-result-object v1
    invoke-virtual {v1}, Ljava/lang/Class;->getName()Ljava/lang/String;
    move-result-object v1
    invoke-virtual {v0, v1}, Lcom/zuoyebang/rlog/logger/CommonBaseEvent;->setExt1(Ljava/lang/String;)V
    invoke-static {v0}, Lcom/zuoyebang/rlog/logger/c;->l(Lcom/zuoyebang/rlog/logger/BaseEvent;)V
    :label_0031
    return-void
.end method

# file_id=0 method_idx=42471 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->createIntent(Landroid/content/Context;Landroid/content/Intent;)Landroid/content/Intent;"
.method public static createIntent(Landroid/content/Context;Landroid/content/Intent;)Landroid/content/Intent;
    .registers 10

    const/4 v0, 0x2
    new-array v1, v0, [Ljava/lang/Object;
    const/4 v2, 0x0
    aput-object v8, v1, v2
    const/4 v3, 0x1
    aput-object v9, v1, v3
    sget-object v4, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    const-class v0, Landroid/content/Context;
    aput-object v0, v6, v2
    const-class v0, Landroid/content/Intent;
    aput-object v0, v6, v3
    const-class v7, Landroid/content/Intent;
    const/4 v2, 0x0
    const/4 v0, 0x1
    const/16 v5, 0x11f8
    move-object v3, v4
    move v4, v0
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v1, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_002a
    iget-object v8, v0, Lcom/meituan/robust/PatchProxyResult;->result:Ljava/lang/Object;
    check-cast v8, Landroid/content/Intent;
    return-object v8
    :label_002a
    new-instance v0, Landroid/content/Intent;
    const-class v1, Lcom/baidu/homework/activity/init/InitActivity;
    invoke-direct {v0, v8, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
    const-string v8, "INPUT_TO_INTENT"
    invoke-virtual {v0, v8, v9}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Landroid/os/Parcelable;)Landroid/content/Intent;
    const/high16 v8, 0x4000000
    invoke-virtual {v0, v8}, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;
    return-object v0
.end method

# file_id=0 method_idx=42472 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->finish()V"
.method public finish()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x120a
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v1
    iget-boolean v1, v1, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_0016
    return-void
    :label_0016
    invoke-super {v8}, Landroid/app/Activity;->finish()V
    iget-boolean v1, v8, Lcom/baidu/homework/activity/init/InitActivity;->k0:Z
    if-eqz v1, :label_0026
    const/high16 v0, 0x10a0000
    const v1, 0x10a0001
    invoke-virtual {v8, v0, v1}, Landroid/app/Activity;->overridePendingTransition(II)V
    goto :label_0029
    :label_0026
    invoke-virtual {v8, v0, v0}, Landroid/app/Activity;->overridePendingTransition(II)V
    :label_0029
    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J
    move-result-wide v0
    iget-wide v2, v8, Lcom/baidu/homework/activity/init/InitActivity;->l0:J
    sub-long/2addr v0, v2
    sput-wide v0, Lcom/baidu/homework/activity/init/l;->a:J
    iget-boolean v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->m0:Z
    invoke-static {v0}, Lh/p/p/a;->e(Z)V
    return-void
.end method

# file_id=0 method_idx=42473 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->init()V"
.method private init()V
    .registers 12

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x11fb
    move-object v2, v11
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v1
    iget-boolean v1, v1, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_0016
    return-void
    :label_0016
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v1
    sput-wide v1, Lcom/baidu/homework/common/g;->l:J
    invoke-virtual {v11}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
    move-result-object v1
    invoke-static {v11, v1}, Lcom/baidu/homework/common/utils/d1;->b(Landroid/app/Activity;Landroid/content/Intent;)Z
    move-result v1
    if-nez v1, :label_002e
    invoke-static {}, Lcom/tencent/bugly/launch/AppLaunchProxy;->getAppLaunch()Lcom/tencent/bugly/launch/AppLaunch;
    move-result-object v0
    invoke-interface {v0}, Lcom/tencent/bugly/launch/AppLaunch;->reportAppFullLaunch()V
    return-void
    :label_002e
    invoke-static {}, Lcom/baidu/homework/activity/init/i;->b()V
    invoke-direct {v11}, Lcom/baidu/homework/activity/init/InitActivity;->P0()V
    invoke-static {}, Lcom/baidu/homework/activity/newhomepage/d_home/c/d;->b()V
    invoke-direct {v11}, Lcom/baidu/homework/activity/init/InitActivity;->z0()V
    invoke-virtual {v11}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
    move-result-object v1
    invoke-virtual {v1}, Landroid/content/Intent;->getExtras()Landroid/os/Bundle;
    move-result-object v1
    if-eqz v1, :label_0059
    invoke-static {}, Lcom/baidu/homework/base/BaseApplication;->getApplication()Landroid/app/Application;
    move-result-object v2
    invoke-static {v2, v1}, Lcom/baidu/homework/common/o/d;->b(Landroid/content/Context;Landroid/os/Bundle;)Z
    move-result v1
    if-eqz v1, :label_0059
    invoke-static {}, Lcom/tencent/bugly/launch/AppLaunchProxy;->getAppLaunch()Lcom/tencent/bugly/launch/AppLaunch;
    move-result-object v0
    invoke-interface {v0}, Lcom/tencent/bugly/launch/AppLaunch;->reportAppFullLaunch()V
    invoke-virtual {v11}, Lcom/baidu/homework/activity/init/InitActivity;->finish()V
    return-void
    :label_0059
    sget-object v1, Lcom/baidu/homework/activity/index/f0/a;->a:Lcom/baidu/homework/activity/index/f0/a;
    invoke-virtual {v1}, Lcom/baidu/homework/activity/index/f0/a;->g()V
    invoke-virtual {v11}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
    move-result-object v1
    const-string v2, "INPUT_FROM_PUSH_TYPE"
    invoke-virtual {v1, v2}, Landroid/content/Intent;->hasExtra(Ljava/lang/String;)Z
    move-result v1
    const/4 v3, 0x2
    const/4 v4, 0x1
    if-eqz v1, :label_00bc
    const/16 v1, 0x8
    new-array v1, v1, [Ljava/lang/String;
    const-string v5, "type"
    aput-object v5, v1, v0
    invoke-virtual {v11}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
    move-result-object v5
    invoke-virtual {v5, v2, v0}, Landroid/content/Intent;->getIntExtra(Ljava/lang/String;I)I
    move-result v2
    invoke-static {v2}, Ljava/lang/String;->valueOf(I)Ljava/lang/String;
    move-result-object v2
    aput-object v2, v1, v4
    const-string v2, "mid"
    aput-object v2, v1, v3
    const/4 v2, 0x3
    invoke-virtual {v11}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
    move-result-object v5
    const-string v6, "INPUT_FROM_PUSH_MID"
    invoke-virtual {v5, v6}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v5
    aput-object v5, v1, v2
    const/4 v2, 0x4
    const-string v5, "push_from"
    aput-object v5, v1, v2
    const/4 v2, 0x5
    invoke-virtual {v11}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
    move-result-object v5
    const-string v6, "INPUT_FROM_PUSH_FROM"
    invoke-virtual {v5, v6}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v5
    aput-object v5, v1, v2
    const/4 v2, 0x6
    const-string v5, "askUrl"
    aput-object v5, v1, v2
    const/4 v2, 0x7
    invoke-virtual {v11}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
    move-result-object v5
    const-string v6, "INPUT_FROM_PUSH_APPLINKURL"
    invoke-virtual {v5, v6}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v5
    aput-object v5, v1, v2
    const-string v2, "OPEN_NOTIFICATION"
    invoke-static {v2, v1}, Lcom/baidu/homework/common/p/g;->v(Ljava/lang/String;[Ljava/lang/String;)V
    :label_00bc
    invoke-static {}, Lh/a/a/a/c/a;->c()Lh/a/a/a/c/a;
    move-result-object v1
    invoke-virtual {v1, v11}, Lh/a/a/a/c/a;->e(Ljava/lang/Object;)V
    sget-object v1, Lcom/baidu/homework/activity/newhomepage/a_home/util/b;->a:Lcom/baidu/homework/activity/newhomepage/a_home/util/b;
    invoke-virtual {v1}, Lcom/baidu/homework/activity/newhomepage/a_home/util/b;->a()V
    sget-object v1, Lcom/baidu/homework/activity/index/x;->a:Lcom/baidu/homework/activity/index/x;
    invoke-virtual {v1, v11}, Lcom/baidu/homework/activity/index/x;->d(Landroid/app/Activity;)V
    :label_00cd
    goto :label_00cf
    :label_00ce
    nop
    :label_00cf
    const-string v1, "INIT_ACTIVITY_CREATE"
    invoke-static {v1}, Lcom/baidu/homework/common/p/g;->r(Ljava/lang/String;)V
    invoke-static {}, Lcom/baidu/homework/common/l/b;->a()V
    const v1, 0x7f0c0052
    const v2, 0x7f0900f4
    invoke-static {v11, v1, v2}, Lcom/baidu/homework/base/r0;->a(Landroid/app/Activity;II)V
    invoke-virtual {v11}, Landroid/app/Activity;->isTaskRoot()Z
    move-result v1
    if-nez v1, :label_0110
    invoke-virtual {v11}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
    move-result-object v1
    invoke-virtual {v1}, Landroid/content/Intent;->getAction()Ljava/lang/String;
    move-result-object v2
    const-string v5, "android.intent.category.LAUNCHER"
    invoke-virtual {v1, v5}, Landroid/content/Intent;->hasCategory(Ljava/lang/String;)Z
    move-result v1
    if-eqz v1, :label_0110
    if-eqz v2, :label_0110
    const-string v1, "android.intent.action.MAIN"
    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-eqz v1, :label_0110
    const-string v0, "START_INIT_WITHOUT_TASK_ROOT"
    invoke-static {v0}, Lcom/baidu/homework/common/p/g;->r(Ljava/lang/String;)V
    invoke-static {}, Lcom/tencent/bugly/launch/AppLaunchProxy;->getAppLaunch()Lcom/tencent/bugly/launch/AppLaunch;
    move-result-object v0
    invoke-interface {v0}, Lcom/tencent/bugly/launch/AppLaunch;->reportAppFullLaunch()V
    invoke-virtual {v11}, Lcom/baidu/homework/activity/init/InitActivity;->finish()V
    return-void
    :label_0110
    invoke-direct {v11}, Lcom/baidu/homework/activity/init/InitActivity;->D0()Z
    move-result v1
    if-nez v1, :label_0120
    invoke-direct {v11}, Lcom/baidu/homework/activity/init/InitActivity;->B0()V
    invoke-static {}, Lcom/baidu/homework/base/h0;->d()Landroid/app/Application;
    move-result-object v1
    invoke-static {v1}, Lcom/baidu/homework/common/utils/s1;->f(Landroid/content/Context;)I
    :label_0120
    invoke-static {v11}, Lcom/baidu/homework/base/BaseApplication;->_setTopActivity(Landroid/app/Activity;)V
    sget-object v1, Lcom/baidu/homework/common/utils/c1;->a:Lcom/baidu/homework/common/utils/c1;
    invoke-virtual {v1, v11}, Lcom/baidu/homework/common/utils/c1;->i(Landroid/app/Activity;)V
    sget-object v1, Lcom/baidu/homework/common/CommonPreference;->KEY_SKIP_NEW_USER_AD_BLOCKER_FOR_UPGRADE_ONCE:Lcom/baidu/homework/common/CommonPreference;
    invoke-static {v1}, Lcom/baidu/homework/common/utils/e1;->b(Ljava/lang/Enum;)Z
    move-result v1
    if-nez v1, :label_017e
    invoke-static {}, Lcom/baidu/homework/common/utils/p;->p()I
    move-result v1
    if-lez v1, :label_017e
    sget-object v1, Lcom/baidu/homework/common/CommonPreference;->KEY_NEW_USER_AD_BLOCKER_SHOW:Lcom/baidu/homework/common/CommonPreference;
    invoke-static {v1}, Lcom/baidu/homework/common/utils/e1;->b(Ljava/lang/Enum;)Z
    move-result v2
    if-eqz v2, :label_017e
    sget-object v2, Lcom/baidu/homework/common/CommonPreference;->KEY_NEW_USER_AD_BLOCKER_TIME:Lcom/baidu/homework/common/CommonPreference;
    invoke-static {v2}, Lcom/baidu/homework/common/utils/e1;->h(Ljava/lang/Enum;)Ljava/lang/Long;
    move-result-object v5
    invoke-virtual {v5}, Ljava/lang/Long;->longValue()J
    move-result-wide v5
    const-wide/16 v7, 0x0
    cmp-long v9, v5, v7
    if-nez v9, :label_0155
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v5
    invoke-static {v2, v5, v6}, Lcom/baidu/homework/common/utils/e1;->y(Ljava/lang/Enum;J)V
    :label_0155
    invoke-static {}, Lcom/baidu/homework/common/utils/p;->p()I
    move-result v2
    if-ne v2, v4, :label_016b
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v7
    sub-long/2addr v7, v5
    const-wide/32 v9, 0x240c8400
    cmp-long v2, v7, v9
    if-lez v2, :label_016b
    invoke-static {v1, v0}, Lcom/baidu/homework/common/utils/e1;->t(Ljava/lang/Enum;Z)V
    goto :label_017e
    :label_016b
    invoke-static {}, Lcom/baidu/homework/common/utils/p;->p()I
    move-result v2
    if-ne v2, v3, :label_017f
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v2
    invoke-static {v5, v6, v2, v3}, Lcom/baidu/homework/common/utils/a0;->g(JJ)Z
    move-result v2
    if-nez v2, :label_017f
    invoke-static {v1, v0}, Lcom/baidu/homework/common/utils/e1;->t(Ljava/lang/Enum;Z)V
    :label_017e
    const/4 v0, 0x1
    :label_017f
    if-eqz v0, :label_0185
    invoke-direct {v11}, Lcom/baidu/homework/activity/init/InitActivity;->A0()V
    goto :label_0188
    :label_0185
    invoke-direct {v11}, Lcom/baidu/homework/activity/init/InitActivity;->C0()V
    :label_0188
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v0
    sput-wide v0, Lcom/baidu/homework/common/g;->m:J
    invoke-static {}, Lcom/tencent/bugly/launch/AppLaunchProxy;->getAppLaunch()Lcom/tencent/bugly/launch/AppLaunch;
    move-result-object v0
    invoke-interface {v0}, Lcom/tencent/bugly/launch/AppLaunch;->reportAppFullLaunch()V
    return-void
    .catchall {:label_00bc .. :label_00cd} :label_00ce
.end method

# file_id=0 method_idx=42474 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->onActivityResult(IILandroid/content/Intent;)V"
.method public onActivityResult(IILandroid/content/Intent;)V
    .registers 13

    const/4 v0, 0x3
    new-array v1, v0, [Ljava/lang/Object;
    new-instance v2, Ljava/lang/Integer;
    invoke-direct {v2, v10}, Ljava/lang/Integer;-><init>(I)V
    const/4 v8, 0x0
    aput-object v2, v1, v8
    new-instance v2, Ljava/lang/Integer;
    invoke-direct {v2, v11}, Ljava/lang/Integer;-><init>(I)V
    const/4 v3, 0x1
    aput-object v2, v1, v3
    const/4 v2, 0x2
    aput-object v12, v1, v2
    sget-object v4, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v0, Ljava/lang/Integer;->TYPE:Ljava/lang/Class;
    aput-object v0, v6, v8
    aput-object v0, v6, v3
    const-class v0, Landroid/content/Intent;
    aput-object v0, v6, v2
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v0, 0x0
    const/16 v5, 0x1208
    move-object v2, v9
    move-object v3, v4
    move v4, v0
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0035
    return-void
    :label_0035
    invoke-super {v9, v10, v11, v12}, Landroidx/fragment/app/FragmentActivity;->onActivityResult(IILandroid/content/Intent;)V
    const/16 v0, 0x3039
    const-string v1, "FORCE_LOGIN_SUCCESS"
    const-string v2, "FORCE_LOGIN_CANCEL"
    const-string v3, "FORCE_LOGIN_SKIP"
    const-string v4, "IS_SKIP"
    if-ne v10, v0, :label_006f
    if-eqz v12, :label_004a
    invoke-virtual {v12, v4, v8}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z
    move-result v8
    :label_004a
    if-eqz v8, :label_0057
    invoke-direct {v9}, Lcom/baidu/homework/activity/init/InitActivity;->w0()V
    invoke-static {v3}, Lcom/baidu/homework/common/p/g;->r(Ljava/lang/String;)V
    invoke-direct {v9}, Lcom/baidu/homework/activity/init/InitActivity;->y0()V
    goto/16 :label_00c5
    :label_0057
    invoke-static {}, Lcom/baidu/homework/common/login/e;->l()Lcom/baidu/homework/common/login/e;
    move-result-object v10
    invoke-virtual {v10}, Lcom/baidu/homework/common/login/e;->w()Z
    move-result v10
    if-eqz v10, :label_0068
    invoke-direct {v9}, Lcom/baidu/homework/activity/init/InitActivity;->w0()V
    invoke-static {v1}, Lcom/baidu/homework/common/p/g;->r(Ljava/lang/String;)V
    goto :label_006b
    :label_0068
    invoke-static {v2}, Lcom/baidu/homework/common/p/g;->r(Ljava/lang/String;)V
    :label_006b
    invoke-direct {v9}, Lcom/baidu/homework/activity/init/InitActivity;->y0()V
    goto :label_00c5
    :label_006f
    const/16 v0, 0x7d0
    if-ne v10, v0, :label_009a
    if-eqz v12, :label_0079
    invoke-virtual {v12, v4, v8}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z
    move-result v8
    :label_0079
    if-eqz v8, :label_0085
    invoke-direct {v9}, Lcom/baidu/homework/activity/init/InitActivity;->w0()V
    invoke-static {v3}, Lcom/baidu/homework/common/p/g;->r(Ljava/lang/String;)V
    invoke-direct {v9}, Lcom/baidu/homework/activity/init/InitActivity;->y0()V
    goto :label_00c5
    :label_0085
    invoke-static {}, Lcom/baidu/homework/common/login/e;->l()Lcom/baidu/homework/common/login/e;
    move-result-object v10
    invoke-virtual {v10}, Lcom/baidu/homework/common/login/e;->w()Z
    move-result v10
    if-eqz v10, :label_0093
    invoke-static {v1}, Lcom/baidu/homework/common/p/g;->r(Ljava/lang/String;)V
    goto :label_0096
    :label_0093
    invoke-static {v2}, Lcom/baidu/homework/common/p/g;->r(Ljava/lang/String;)V
    :label_0096
    invoke-direct {v9}, Lcom/baidu/homework/activity/init/InitActivity;->y0()V
    goto :label_00c5
    :label_009a
    const/16 v0, 0x271b
    if-ne v10, v0, :label_00a2
    invoke-static {v9, v10, v11, v12}, Lcom/baidu/homework/base/BaseApplication;->onSplashActivityResult(Landroid/app/Activity;IILandroid/content/Intent;)V
    goto :label_00c5
    :label_00a2
    const/16 v11, 0x3f3
    if-ne v10, v11, :label_00aa
    invoke-direct {v9}, Lcom/baidu/homework/activity/init/InitActivity;->y0()V
    goto :label_00c5
    :label_00aa
    const/16 v12, 0x3e9
    if-ne v10, v12, :label_00c5
    invoke-static {}, Lcom/baidu/homework/common/login/e;->l()Lcom/baidu/homework/common/login/e;
    move-result-object v10
    invoke-virtual {v10}, Lcom/baidu/homework/common/login/e;->w()Z
    move-result v10
    if-eqz v10, :label_00bc
    invoke-direct {v9}, Lcom/baidu/homework/activity/init/InitActivity;->y0()V
    goto :label_00c5
    :label_00bc
    const-string v10, ""
    invoke-static {v9, v10}, Lcom/baidu/homework/common/login/core/RegisterSelectGradeActivity;->createIntent(Landroid/content/Context;Ljava/lang/String;)Landroid/content/Intent;
    move-result-object v10
    invoke-virtual {v9, v10, v11}, Lcom/baidu/homework/activity/init/InitActivity;->startActivityForResult(Landroid/content/Intent;I)V
    :label_00c5
    return-void
.end method

# file_id=0 method_idx=42475 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->onBackPressed()V"
.method public onBackPressed()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1202
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0016
    return-void
    :label_0016
    invoke-super {v8}, Landroidx/activity/ComponentActivity;->onBackPressed()V
    :label_0019
    goto :label_001e
    :label_001a
    move-exception v0
    invoke-virtual {v0}, Ljava/lang/IllegalStateException;->printStackTrace()V
    :label_001e
    return-void
    .catch Ljava/lang/IllegalStateException; {:label_0016 .. :label_0019} :label_001a
.end method

# file_id=0 method_idx=42476 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->onCreate(Landroid/os/Bundle;)V"
.method public onCreate(Landroid/os/Bundle;)V
    .registers 10

    const/4 v0, 0x1
    new-array v1, v0, [Ljava/lang/Object;
    const/4 v2, 0x0
    aput-object v9, v1, v2
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    const-class v4, Landroid/os/Bundle;
    aput-object v4, v6, v2
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x11fa
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v1
    iget-boolean v1, v1, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_001d
    return-void
    :label_001d
    invoke-super {v8, v9}, Landroidx/fragment/app/FragmentActivity;->onCreate(Landroid/os/Bundle;)V
    :label_0020
    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J
    move-result-wide v1
    iput-wide v1, v8, Lcom/baidu/homework/activity/init/InitActivity;->l0:J
    invoke-static {}, Lh/p/p/a;->d()V
    new-instance v9, Lcom/baidu/homework/activity/init/f;
    invoke-direct {v9, v8}, Lcom/baidu/homework/activity/init/f;-><init>(Lcom/baidu/homework/activity/init/InitActivity;)V
    invoke-static {v8, v0, v9}, Lcom/baidu/homework/base/r0;->j(Landroid/app/Activity;ZLcom/baidu/homework/base/e0;)V
    return-void
    .catch Ljava/lang/NullPointerException; {:label_001d .. :label_0020} :label_0020
.end method

# file_id=0 method_idx=42477 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->onDestroy()V"
.method public onDestroy()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x120b
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0016
    return-void
    :label_0016
    invoke-super {v8}, Landroidx/fragment/app/FragmentActivity;->onDestroy()V
    const/4 v0, 0x0
    iput-object v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->p0:Lcom/homework/abtest/i;
    :label_001c
    iget-object v1, v8, Lcom/baidu/homework/activity/init/InitActivity;->u0:Lcom/baidu/homework/ads/a;
    invoke-virtual {v1, v0}, Landroid/os/Handler;->removeCallbacksAndMessages(Ljava/lang/Object;)V
    :label_0021
    goto :label_0026
    :label_0022
    move-exception v0
    invoke-virtual {v0}, Ljava/lang/Exception;->printStackTrace()V
    :label_0026
    return-void
    .catch Ljava/lang/Exception; {:label_001c .. :label_0021} :label_0022
.end method

# file_id=0 method_idx=42478 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->onNewIntent(Landroid/content/Intent;)V"
.method public onNewIntent(Landroid/content/Intent;)V
    .registers 10

    const/4 v0, 0x1
    new-array v1, v0, [Ljava/lang/Object;
    const/4 v2, 0x0
    aput-object v9, v1, v2
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    const-class v0, Landroid/content/Intent;
    aput-object v0, v6, v2
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x11fc
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_001d
    return-void
    :label_001d
    invoke-super {v8, v9}, Landroidx/fragment/app/FragmentActivity;->onNewIntent(Landroid/content/Intent;)V
    invoke-static {}, Lcom/baidu/homework/base/r0;->i()Z
    move-result v9
    if-nez v9, :label_002b
    sget-object v9, Lcom/baidu/homework/common/utils/c1;->a:Lcom/baidu/homework/common/utils/c1;
    invoke-virtual {v9, v8}, Lcom/baidu/homework/common/utils/c1;->i(Landroid/app/Activity;)V
    :label_002b
    return-void
.end method

# file_id=0 method_idx=42479 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->onPause()V"
.method public onPause()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x120d
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0016
    return-void
    :label_0016
    invoke-super {v8}, Landroidx/fragment/app/FragmentActivity;->onPause()V
    const/4 v0, 0x1
    iput-boolean v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->n0:Z
    return-void
.end method

# file_id=0 method_idx=42480 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->onResume()V"
.method public onResume()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x120c
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0016
    return-void
    :label_0016
    invoke-super {v8}, Landroidx/fragment/app/FragmentActivity;->onResume()V
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v0
    sput-wide v0, Lcom/baidu/homework/common/g;->n:J
    iget-boolean v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->n0:Z
    if-eqz v0, :label_0026
    invoke-virtual {v8}, Lcom/baidu/homework/activity/init/InitActivity;->u0()V
    :label_0026
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v0
    sput-wide v0, Lcom/baidu/homework/common/g;->o:J
    return-void
.end method

# file_id=0 method_idx=42481 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->onWindowFocusChanged(Z)V"
.method public onWindowFocusChanged(Z)V
    .registers 10

    const/4 v0, 0x1
    new-array v1, v0, [Ljava/lang/Object;
    new-instance v2, Ljava/lang/Byte;
    invoke-direct {v2, v9}, Ljava/lang/Byte;-><init>(B)V
    const/4 v3, 0x0
    aput-object v2, v1, v3
    sget-object v4, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v0, Ljava/lang/Boolean;->TYPE:Ljava/lang/Class;
    aput-object v0, v6, v3
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v0, 0x0
    const/16 v5, 0x11f9
    move-object v2, v8
    move-object v3, v4
    move v4, v0
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0024
    return-void
    :label_0024
    invoke-super {v8, v9}, Landroid/app/Activity;->onWindowFocusChanged(Z)V
    sget-wide v0, Lcom/baidu/homework/base/BaseApplication;->mColdBootStartTime:J
    const-wide/16 v2, 0x0
    cmp-long v9, v0, v2
    if-lez v9, :label_0039
    invoke-static {}, Lcom/baidu/homework/base/r0;->i()Z
    move-result v9
    if-nez v9, :label_0039
    const-wide/16 v0, -0x1
    sput-wide v0, Lcom/baidu/homework/base/BaseApplication;->mColdBootStartTime:J
    :label_0039
    return-void
.end method

# file_id=0 method_idx=42482 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->s0(Lcom/baidu/homework/activity/init/InitActivity;)Lcom/baidu/homework/ads/a;"
.method static synthetic s0(Lcom/baidu/homework/activity/init/InitActivity;)Lcom/baidu/homework/ads/a;
    .registers 1

    iget-object v0, v0, Lcom/baidu/homework/activity/init/InitActivity;->u0:Lcom/baidu/homework/ads/a;
    return-object v0
.end method

# file_id=0 method_idx=42483 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->setContentView(I)V"
.method public setContentView(I)V
    .registers 10

    const/4 v0, 0x1
    new-array v1, v0, [Ljava/lang/Object;
    new-instance v2, Ljava/lang/Integer;
    invoke-direct {v2, v9}, Ljava/lang/Integer;-><init>(I)V
    const/4 v3, 0x0
    aput-object v2, v1, v3
    sget-object v4, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v0, Ljava/lang/Integer;->TYPE:Ljava/lang/Class;
    aput-object v0, v6, v3
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v0, 0x0
    const/16 v5, 0x11ff
    move-object v2, v8
    move-object v3, v4
    move v4, v0
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0024
    return-void
    :label_0024
    invoke-super {v8, v9}, Landroidx/activity/ComponentActivity;->setContentView(I)V
    invoke-static {v8}, Lcom/baidu/homework/base/BaseApplication;->checkSplashOnCreate(Landroid/app/Activity;)V
    return-void
.end method

# file_id=0 method_idx=42484 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->setContentView(Landroid/view/View;)V"
.method public setContentView(Landroid/view/View;)V
    .registers 10

    const/4 v0, 0x1
    new-array v1, v0, [Ljava/lang/Object;
    const/4 v2, 0x0
    aput-object v9, v1, v2
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    const-class v0, Landroid/view/View;
    aput-object v0, v6, v2
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1200
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_001d
    return-void
    :label_001d
    invoke-super {v8, v9}, Landroidx/activity/ComponentActivity;->setContentView(Landroid/view/View;)V
    invoke-static {v8}, Lcom/baidu/homework/base/BaseApplication;->checkSplashOnCreate(Landroid/app/Activity;)V
    return-void
.end method

# file_id=0 method_idx=42485 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->setContentView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V"
.method public setContentView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V
    .registers 11

    const/4 v0, 0x2
    new-array v1, v0, [Ljava/lang/Object;
    const/4 v2, 0x0
    aput-object v9, v1, v2
    const/4 v3, 0x1
    aput-object v10, v1, v3
    sget-object v4, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    const-class v0, Landroid/view/View;
    aput-object v0, v6, v2
    const-class v0, Landroid/view/ViewGroup$LayoutParams;
    aput-object v0, v6, v3
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v0, 0x0
    const/16 v5, 0x1201
    move-object v2, v8
    move-object v3, v4
    move v4, v0
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0026
    return-void
    :label_0026
    invoke-super {v8, v9, v10}, Landroidx/activity/ComponentActivity;->setContentView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V
    invoke-static {v8}, Lcom/baidu/homework/base/BaseApplication;->checkSplashOnCreate(Landroid/app/Activity;)V
    return-void
.end method

# file_id=0 method_idx=42486 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->startActivityForResult(Landroid/content/Intent;I)V"
.method public startActivityForResult(Landroid/content/Intent;I)V
    .registers 13

    const/4 v0, 0x2
    new-array v1, v0, [Ljava/lang/Object;
    const/4 v8, 0x0
    aput-object v11, v1, v8
    new-instance v2, Ljava/lang/Integer;
    invoke-direct {v2, v12}, Ljava/lang/Integer;-><init>(I)V
    const/4 v9, 0x1
    aput-object v2, v1, v9
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    const-class v2, Landroid/content/Intent;
    aput-object v2, v6, v8
    sget-object v2, Ljava/lang/Integer;->TYPE:Ljava/lang/Class;
    aput-object v2, v6, v9
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1209
    move-object v2, v10
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v1
    iget-boolean v1, v1, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_0029
    return-void
    :label_0029
    iget-boolean v1, v10, Lcom/baidu/homework/activity/init/InitActivity;->j0:Z
    iput-boolean v1, v10, Lcom/baidu/homework/activity/init/InitActivity;->k0:Z
    invoke-static {}, Lcom/baidu/homework/base/r0;->i()Z
    move-result v1
    if-nez v1, :label_0058
    const/4 v1, 0x6
    new-array v1, v1, [Ljava/lang/String;
    const-string v2, "block"
    aput-object v2, v1, v8
    const-string v2, "false"
    aput-object v2, v1, v9
    const-string v2, "islimit"
    aput-object v2, v1, v0
    const/4 v0, 0x3
    invoke-static {v8}, Ljava/lang/String;->valueOf(Z)Ljava/lang/String;
    move-result-object v2
    aput-object v2, v1, v0
    const/4 v0, 0x4
    const-string v2, "sstate"
    aput-object v2, v1, v0
    const/4 v0, 0x5
    const-string v2, ""
    aput-object v2, v1, v0
    const-string v0, "INIT_START_ACTIVITY"
    invoke-static {v0, v1}, Lcom/baidu/homework/common/p/g;->v(Ljava/lang/String;[Ljava/lang/String;)V
    :label_0058
    invoke-super {v10, v11, v12}, Landroidx/activity/ComponentActivity;->startActivityForResult(Landroid/content/Intent;I)V
    iget-boolean v11, v10, Lcom/baidu/homework/activity/init/InitActivity;->k0:Z
    if-eqz v11, :label_0068
    const/high16 v11, 0x10a0000
    const v12, 0x10a0001
    invoke-virtual {v10, v11, v12}, Landroid/app/Activity;->overridePendingTransition(II)V
    goto :label_0070
    :label_0068
    invoke-virtual {v10, v8, v8}, Landroid/app/Activity;->overridePendingTransition(II)V
    :label_006b
    goto :label_0070
    :label_006c
    move-exception v11
    invoke-virtual {v11}, Ljava/lang/Exception;->printStackTrace()V
    :label_0070
    return-void
    .catch Ljava/lang/Exception; {:label_0058 .. :label_006b} :label_006c
.end method

# file_id=0 method_idx=42487 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->startActivityForResult(Landroid/content/Intent;ILandroid/os/Bundle;)V"
.method public startActivityForResult(Landroid/content/Intent;ILandroid/os/Bundle;)V
    .registers 12

    const/4 v0, 0x3
    new-array v1, v0, [Ljava/lang/Object;
    const/4 v2, 0x0
    aput-object v9, v1, v2
    new-instance v3, Ljava/lang/Integer;
    invoke-direct {v3, v10}, Ljava/lang/Integer;-><init>(I)V
    const/4 v4, 0x1
    aput-object v3, v1, v4
    const/4 v3, 0x2
    aput-object v11, v1, v3
    sget-object v5, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    const-class v0, Landroid/content/Intent;
    aput-object v0, v6, v2
    sget-object v0, Ljava/lang/Integer;->TYPE:Ljava/lang/Class;
    aput-object v0, v6, v4
    const-class v0, Landroid/os/Bundle;
    aput-object v0, v6, v3
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v0, 0x120e
    move-object v2, v8
    move-object v3, v5
    move v5, v0
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0032
    return-void
    :label_0032
    sget-object v0, Lcom/baidu/homework/common/utils/c1;->a:Lcom/baidu/homework/common/utils/c1;
    invoke-virtual {v0, v9}, Lcom/baidu/homework/common/utils/c1;->g(Landroid/content/Intent;)Z
    move-result v0
    if-eqz v0, :label_003b
    return-void
    :label_003b
    invoke-super {v8, v9, v10, v11}, Landroidx/activity/ComponentActivity;->startActivityForResult(Landroid/content/Intent;ILandroid/os/Bundle;)V
    :label_003e
    goto :label_0049
    :label_003f
    move-exception v9
    invoke-virtual {v9}, Ljava/lang/Throwable;->printStackTrace()V
    invoke-static {}, Lcom/baidu/homework/base/BaseApplication;->isQaOrDebug()Z
    move-result v10
    if-nez v10, :label_004a
    :label_0049
    return-void
    :label_004a
    throw v9
    .catchall {:label_003b .. :label_003e} :label_003f
.end method

# file_id=0 method_idx=42488 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->t0(Lcom/baidu/homework/activity/init/InitActivity;)Z"
.method static synthetic t0(Lcom/baidu/homework/activity/init/InitActivity;)Z
    .registers 1

    iget-boolean v0, v0, Lcom/baidu/homework/activity/init/InitActivity;->n0:Z
    return v0
.end method

# file_id=0 method_idx=42489 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->u0()V"
.method public u0()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x11fe
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0016
    return-void
    :label_0016
    const/4 v0, 0x1
    iput-boolean v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->s0:Z
    iget-boolean v1, v8, Lcom/baidu/homework/activity/init/InitActivity;->r0:Z
    if-eqz v1, :label_0032
    iget-object v1, v8, Lcom/baidu/homework/activity/init/InitActivity;->v0:Ljava/lang/Runnable;
    if-eqz v1, :label_0032
    iget-boolean v1, v8, Lcom/baidu/homework/activity/init/InitActivity;->t0:Z
    if-nez v1, :label_0032
    iget-object v1, v8, Lcom/baidu/homework/activity/init/InitActivity;->u0:Lcom/baidu/homework/ads/a;
    const/4 v2, 0x0
    invoke-virtual {v1, v2}, Landroid/os/Handler;->removeCallbacksAndMessages(Ljava/lang/Object;)V
    iput-boolean v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->t0:Z
    iget-object v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->v0:Ljava/lang/Runnable;
    invoke-interface {v0}, Ljava/lang/Runnable;->run()V
    :label_0032
    return-void
.end method

# file_id=0 method_idx=42490 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->v0(Lcom/baidu/homework/base/e0;)V"
.method private v0(Lcom/baidu/homework/base/e0;)V
    .registers 10

    const/4 v0, 0x1
    new-array v1, v0, [Ljava/lang/Object;
    const/4 v2, 0x0
    aput-object v9, v1, v2
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    const-class v0, Lcom/baidu/homework/base/e0;
    aput-object v0, v6, v2
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1206
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_001d
    return-void
    :label_001d
    invoke-static {}, Lcom/homework/abtest/d;->f()Z
    move-result v0
    if-eqz v0, :label_0029
    if-eqz v9, :label_0028
    invoke-interface {v9}, Lcom/baidu/homework/base/e0;->call()V
    :label_0028
    return-void
    :label_0029
    iget-object v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->p0:Lcom/homework/abtest/i;
    if-nez v0, :label_0033
    if-eqz v9, :label_0032
    invoke-interface {v9}, Lcom/baidu/homework/base/e0;->call()V
    :label_0032
    return-void
    :label_0033
    new-instance v1, Lcom/baidu/homework/activity/init/a;
    invoke-direct {v1, v9}, Lcom/baidu/homework/activity/init/a;-><init>(Lcom/baidu/homework/base/e0;)V
    invoke-virtual {v0, v1}, Lcom/homework/abtest/i;->q(Lcom/homework/abtest/k/c;)Lcom/homework/abtest/i;
    move-result-object v0
    invoke-virtual {v0}, Lcom/homework/abtest/i;->m()V
    :label_003f
    goto :label_0046
    :label_0040
    nop
    if-eqz v9, :label_0046
    invoke-interface {v9}, Lcom/baidu/homework/base/e0;->call()V
    :label_0046
    return-void
    .catch Ljava/lang/Exception; {:label_0033 .. :label_003f} :label_0040
.end method

# file_id=0 method_idx=42491 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->w0()V"
.method private w0()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1207
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0016
    return-void
    :label_0016
    sget-object v0, Lcom/baidu/homework/activity/index/IndexPreference;->FORCE_LOGIN_SHOW_COUNT:Lcom/baidu/homework/activity/index/IndexPreference;
    invoke-static {v0}, Lcom/baidu/homework/common/utils/e1;->f(Ljava/lang/Enum;)I
    move-result v1
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v2
    sget-object v4, Lcom/baidu/homework/activity/index/IndexPreference;->FORCE_LOGIN_LAST_SHOW_TIME:Lcom/baidu/homework/activity/index/IndexPreference;
    invoke-static {v4, v2, v3}, Lcom/baidu/homework/common/utils/e1;->y(Ljava/lang/Enum;J)V
    add-int/lit8 v1, v1, 0x1
    invoke-static {v0, v1}, Lcom/baidu/homework/common/utils/e1;->w(Ljava/lang/Enum;I)V
    return-void
.end method

# file_id=0 method_idx=42492 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->x0()Landroid/content/Intent;"
.method private x0()Landroid/content/Intent;
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    const-class v7, Landroid/content/Intent;
    const/4 v4, 0x0
    const/16 v5, 0x1204
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v1, v0, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_001a
    iget-object v0, v0, Lcom/meituan/robust/PatchProxyResult;->result:Ljava/lang/Object;
    check-cast v0, Landroid/content/Intent;
    return-object v0
    :label_001a
    iget v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->h0:I
    const/4 v1, -0x1
    if-ne v0, v1, :label_0024
    invoke-static {v8}, Lcom/baidu/homework/activity/index/IndexActivity;->createIntent(Landroid/content/Context;)Landroid/content/Intent;
    move-result-object v0
    goto :label_002a
    :label_0024
    iget v1, v8, Lcom/baidu/homework/activity/init/InitActivity;->i0:I
    invoke-static {v8, v0, v1}, Lcom/baidu/homework/activity/index/IndexActivity;->createIntent(Landroid/content/Context;II)Landroid/content/Intent;
    move-result-object v0
    :label_002a
    return-object v0
.end method

# file_id=0 method_idx=42493 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->y0()V"
.method private y0()V
    .registers 11

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1203
    move-object v2, v10
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v1
    iget-boolean v1, v1, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_0016
    return-void
    :label_0016
    invoke-virtual {v10}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
    move-result-object v1
    const-string v2, "LAUNCH_FROM_SCHEMA"
    const-string v3, "from"
    if-eqz v1, :label_003c
    invoke-virtual {v1}, Landroid/content/Intent;->getData()Landroid/net/Uri;
    move-result-object v4
    if-eqz v4, :label_003c
    invoke-virtual {v4, v3}, Landroid/net/Uri;->getQueryParameter(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v5
    if-eqz v5, :label_002f
    invoke-static {v2, v5}, Lcom/baidu/homework/common/p/g;->u(Ljava/lang/String;Ljava/lang/String;)V
    :label_002f
    const-string v5, "refer"
    invoke-virtual {v4, v5}, Landroid/net/Uri;->getQueryParameter(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v4
    if-eqz v4, :label_003c
    const-string v5, "LAUNCH_FROM_APPLINK"
    invoke-static {v5, v4}, Lcom/baidu/homework/common/p/g;->u(Ljava/lang/String;Ljava/lang/String;)V
    :label_003c
    const/4 v4, 0x0
    if-nez v1, :label_0045
    invoke-direct {v10}, Lcom/baidu/homework/activity/init/InitActivity;->x0()Landroid/content/Intent;
    move-result-object v0
    goto/16 :label_0101
    :label_0045
    const-string v5, "INPUT_TO_INTENT"
    invoke-virtual {v1, v5}, Landroid/content/Intent;->hasExtra(Ljava/lang/String;)Z
    move-result v6
    if-eqz v6, :label_005b
    invoke-virtual {v1, v5}, Landroid/content/Intent;->getParcelableExtra(Ljava/lang/String;)Landroid/os/Parcelable;
    move-result-object v6
    if-eqz v6, :label_005b
    invoke-virtual {v1, v5}, Landroid/content/Intent;->getParcelableExtra(Ljava/lang/String;)Landroid/os/Parcelable;
    move-result-object v0
    check-cast v0, Landroid/content/Intent;
    goto/16 :label_0101
    :label_005b
    invoke-virtual {v1}, Landroid/content/Intent;->getData()Landroid/net/Uri;
    move-result-object v1
    if-nez v1, :label_0067
    invoke-direct {v10}, Lcom/baidu/homework/activity/init/InitActivity;->x0()Landroid/content/Intent;
    move-result-object v0
    goto/16 :label_0101
    :label_0067
    invoke-virtual {v1}, Landroid/net/Uri;->toString()Ljava/lang/String;
    move-result-object v5
    invoke-virtual {v1}, Landroid/net/Uri;->getPath()Ljava/lang/String;
    move-result-object v6
    invoke-virtual {v1}, Landroid/net/Uri;->getHost()Ljava/lang/String;
    move-result-object v7
    invoke-virtual {v1}, Landroid/net/Uri;->getScheme()Ljava/lang/String;
    move-result-object v8
    invoke-static {v5}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
    move-result v9
    if-eqz v9, :label_0083
    invoke-direct {v10}, Lcom/baidu/homework/activity/init/InitActivity;->x0()Landroid/content/Intent;
    move-result-object v0
    goto/16 :label_0101
    :label_0083
    const-string v9, "/jumpApp"
    invoke-virtual {v9, v6}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v6
    const-string v9, ""
    if-eqz v6, :label_00a2
    const-string v5, "url"
    invoke-static {v1, v5, v9}, Lcom/baidu/homework/common/utils/p0;->l(Landroid/net/Uri;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v5
    invoke-static {v5}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
    move-result v6
    if-nez v6, :label_00b9
    :label_0099
    const-string v6, "UTF-8"
    invoke-static {v5, v6}, Ljava/net/URLDecoder;->decode(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v5
    :label_009f
    goto :label_00b9
    :label_00a0
    nop
    goto :label_00b9
    :label_00a2
    new-instance v6, Landroid/net/Uri$Builder;
    invoke-direct {v6}, Landroid/net/Uri$Builder;-><init>()V
    invoke-virtual {v6, v8}, Landroid/net/Uri$Builder;->scheme(Ljava/lang/String;)Landroid/net/Uri$Builder;
    move-result-object v6
    invoke-virtual {v6, v7}, Landroid/net/Uri$Builder;->authority(Ljava/lang/String;)Landroid/net/Uri$Builder;
    move-result-object v6
    invoke-virtual {v6}, Landroid/net/Uri$Builder;->toString()Ljava/lang/String;
    move-result-object v6
    const-string v7, "app://homework"
    invoke-virtual {v5, v6, v7}, Ljava/lang/String;->replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;
    move-result-object v5
    :label_00b9
    sget-object v6, Lcom/baidu/homework/activity/init/j;->a:Lcom/baidu/homework/activity/init/j;
    invoke-virtual {v6, v5}, Lcom/baidu/homework/activity/init/j;->b(Ljava/lang/String;)Z
    move-result v6
    if-eqz v6, :label_00fd
    invoke-static {v10, v5}, Lcom/baidu/homework/common/utils/p0;->c(Landroid/content/Context;Ljava/lang/String;)Landroid/content/Intent;
    move-result-object v5
    if-eqz v5, :label_00f8
    const-string v6, "stayApp"
    invoke-static {v1, v6, v0}, Lcom/baidu/homework/common/utils/p0;->m(Landroid/net/Uri;Ljava/lang/String;I)I
    move-result v6
    const/4 v7, 0x1
    if-ne v6, v7, :label_00d4
    invoke-static {v10}, Lcom/baidu/homework/activity/index/IndexActivity;->createIntent(Landroid/content/Context;)Landroid/content/Intent;
    move-result-object v4
    :label_00d4
    invoke-static {v1, v3, v9}, Lcom/baidu/homework/common/utils/p0;->l(Landroid/net/Uri;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v3
    invoke-static {v3}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
    move-result v6
    if-nez v6, :label_00f6
    const/4 v6, 0x4
    new-array v6, v6, [Ljava/lang/String;
    const-string v8, "f"
    aput-object v8, v6, v0
    aput-object v3, v6, v7
    const/4 v0, 0x2
    const-string v3, "uri"
    aput-object v3, v6, v0
    const/4 v0, 0x3
    invoke-virtual {v1}, Landroid/net/Uri;->toString()Ljava/lang/String;
    move-result-object v1
    aput-object v1, v6, v0
    invoke-static {v2, v6}, Lcom/baidu/homework/common/p/g;->v(Ljava/lang/String;[Ljava/lang/String;)V
    :label_00f6
    move-object v0, v5
    goto :label_0101
    :label_00f8
    invoke-direct {v10}, Lcom/baidu/homework/activity/init/InitActivity;->x0()Landroid/content/Intent;
    move-result-object v0
    goto :label_0101
    :label_00fd
    invoke-direct {v10}, Lcom/baidu/homework/activity/init/InitActivity;->x0()Landroid/content/Intent;
    move-result-object v0
    :label_0101
    iget-object v1, v10, Lcom/baidu/homework/activity/init/InitActivity;->o0:Lcom/baidu/homework/activity/init/InitActivity$a;
    invoke-virtual {v1, v0, v4}, Lcom/baidu/homework/activity/init/InitActivity$a;->a(Landroid/content/Intent;Landroid/content/Intent;)Lcom/baidu/homework/activity/init/InitActivity$a;
    iget-object v0, v10, Lcom/baidu/homework/activity/init/InitActivity;->o0:Lcom/baidu/homework/activity/init/InitActivity$a;
    invoke-virtual {v0}, Lcom/baidu/homework/activity/init/InitActivity$a;->run()V
    return-void
    .catch Ljava/lang/Exception; {:label_0099 .. :label_009f} :label_00a0
.end method

# file_id=0 method_idx=42494 source="base.apk" entry="classes.dex" descriptor="Lcom/baidu/homework/activity/init/InitActivity;->z0()V"
.method private z0()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/baidu/homework/activity/init/InitActivity;->changeQuickRedirect:Lcom/meituan/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1205
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/meituan/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/meituan/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/meituan/robust/PatchProxyResult;
    move-result-object v1
    iget-boolean v1, v1, Lcom/meituan/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_0016
    return-void
    :label_0016
    invoke-static {}, Lcom/homework/abtest/d;->h()Z
    move-result v1
    if-eqz v1, :label_001d
    return-void
    :label_001d
    invoke-static {}, Lcom/baidu/homework/base/BaseApplication;->isQaOrDebug()Z
    move-result v1
    if-eqz v1, :label_0030
    invoke-static {}, Lcom/baidu/homework/base/p0;->f()Ljava/lang/String;
    move-result-object v1
    const-string v2, "suanshubang"
    invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
    move-result v1
    if-eqz v1, :label_0030
    const/4 v0, 0x1
    :label_0030
    new-instance v1, Ljava/util/HashMap;
    invoke-direct {v1}, Ljava/util/HashMap;-><init>()V
    invoke-static {}, Lcom/baidu/homework/base/BaseApplication;->isGray()Z
    move-result v2
    if-eqz v2, :label_003e
    const-string v2, "1"
    goto :label_0040
    :label_003e
    const-string v2, "0"
    :label_0040
    const-string v3, "isGrayVersion"
    invoke-virtual {v1, v3, v2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    const-string v2, "appid"
    const-string v3, "knowledge"
    invoke-virtual {v1, v2, v3}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    invoke-static {}, Lcom/baidu/homework/base/BaseApplication;->getApplication()Landroid/app/Application;
    move-result-object v2
    invoke-static {v2}, Lcom/homework/abtest/d;->g(Landroid/content/Context;)Lcom/homework/abtest/h;
    move-result-object v2
    invoke-static {}, Lcom/baidu/homework/base/BaseApplication;->isQaOrDebug()Z
    move-result v3
    invoke-virtual {v2, v3}, Lcom/homework/abtest/h;->b(Z)Lcom/homework/abtest/h;
    move-result-object v2
    if-eqz v0, :label_0061
    sget-object v0, Lcom/homework/abtest/i;->g:Ljava/lang/String;
    goto :label_0063
    :label_0061
    sget-object v0, Lcom/homework/abtest/i;->f:Ljava/lang/String;
    :label_0063
    invoke-virtual {v2, v0}, Lcom/homework/abtest/h;->d(Ljava/lang/String;)Lcom/homework/abtest/h;
    move-result-object v0
    invoke-static {}, Lcom/baidu/homework/base/BaseApplication;->isQaOrDebug()Z
    move-result v2
    if-eqz v2, :label_0070
    const/16 v2, 0x9c4
    goto :label_0072
    :label_0070
    const/16 v2, 0x5dc
    :label_0072
    invoke-virtual {v0, v2}, Lcom/homework/abtest/h;->h(I)Lcom/homework/abtest/h;
    move-result-object v0
    invoke-virtual {v0, v1}, Lcom/homework/abtest/h;->c(Ljava/util/HashMap;)Lcom/homework/abtest/h;
    move-result-object v0
    new-instance v1, Lcom/baidu/homework/activity/init/h;
    invoke-direct {v1}, Lcom/baidu/homework/activity/init/h;-><init>()V
    invoke-virtual {v0, v1}, Lcom/homework/abtest/h;->f(Lcom/homework/abtest/k/a;)Lcom/homework/abtest/i;
    move-result-object v0
    iput-object v0, v8, Lcom/baidu/homework/activity/init/InitActivity;->p0:Lcom/homework/abtest/i;
    return-void
.end method

